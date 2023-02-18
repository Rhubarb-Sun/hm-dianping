package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(2L, 100L);
    }

    private ExecutorService es = Executors.newFixedThreadPool(600);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(400);

        Runnable task = () -> {
            for (int i = 0; i < 200; i++) {
                long id = redisIdWorker.nextId("order");

                log.info("id = {}", id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 400; i++) {
            es.submit(task);
        }
        long end  = System.currentTimeMillis();

        latch.await();

        log.info("latch = {}", end - begin);
    }
}
