package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Component
public class RedisIdWorker {
    private final StringRedisTemplate stringRedisTemplate;
    // 序列号位数
    private static final long COUNT_BIT = 32;

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long  count = stringRedisTemplate.opsForValue().increment("ic.r:" + keyPrefix + ":" + date);
        //3.拼接返回
        return timestamp << COUNT_BIT | count;
    }

}
