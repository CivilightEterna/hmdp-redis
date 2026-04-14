package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R>dbFallback, Long time, TimeUnit unit){
        //1.从redis中添加缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断是否命中空缓存
        if(json != null){
            return null;
        }
        //4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //5.不存在，返回错误
        if(r == null){
            //将空值缓存，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", time, unit );
            return null;
        }
        //6.存在，写入redis，并且实现超时剔除
        this.set(key, r, time, unit );
        return r;
    }


    private  static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <ID,R>R queryLogicExpire(String keyPrefix,ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中添加缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(json)){
            //3.不存在，查询数据库
            R r = dbFallback.apply(id);
            if(r == null){
                return null;
            }
            //写入缓存
            this.setWithLogicalExpire(key, r, time, unit);
            return r;
        }

        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5.判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            //5.1未过期，直接返回
            return r;
        }
        //5.2过期，需要缓存重建
        //6.重构缓存
        //6.1获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        //6.2判断是否获取锁成功
        if(isLock){
            //6.3成功，获取锁成功，开启独立线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    if(r1 != null){
                        //写入redis
                        this.setWithLogicalExpire(key, r1, time,  unit);
                    }
                } catch (Exception  e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }

            });
        }
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
