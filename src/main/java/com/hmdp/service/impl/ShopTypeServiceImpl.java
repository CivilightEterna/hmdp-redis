package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.hmdp.utils.RedisConstants;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> list = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(list);
        }
        //  命中空缓存
        if (shopTypeJson != null) {
            return Result.fail("店铺类型不存在");
        }
        List<ShopType> typeList = this.query().orderByAsc("sort").list();

        //判空，防止缓存穿透
        if (typeList == null || typeList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, "", RedisConstants.CACHE_NULL_TTL, java.util.concurrent.TimeUnit.MINUTES);
            return Result.fail("店铺类型不存在");

        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList), RedisConstants.CACHE_SHOP_TYPE_TTL, java.util.concurrent.TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
