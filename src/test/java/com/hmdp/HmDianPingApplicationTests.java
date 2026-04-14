package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.CacheClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    public ShopServiceImpl shopService;
    @Test
    public void testSaveShop() throws InterruptedException{
        Result shop = shopService.queryById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop,10L, TimeUnit.SECONDS);
    }
}
