package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 查询：解决缓存穿透
        // Shop shop = queryWithoutCachePenetration(id);

        // 解决缓存击穿方案一：互斥锁
        // Shop shop = queryWithMutexLock(id);

        // 解决缓存击穿方案二：逻辑过期
         Shop shop = queryWithLogicExpiration(id);
        if (shop == null) {
            log.info("商铺不存在！");
            return Result.fail("商铺不存在！");
        }
        // return ok.
        return Result.ok(shop);
    }

    /**
     * 查询：解决缓存击穿/热点失效
     * 方案一：互斥锁
     */
    private Shop queryWithMutexLock(Long id) {
        // 1. query from cache.
        // 用hash和string类型都可以。value无需修改，用string也好。
        String key = CACHE_SHOP_KEY + id;
        String cachedShopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. not null, return
        if (StrUtil.isNotBlank(cachedShopJson)) {
//            log.info("成功从缓存中获取到了店铺 {} 的信息：{}", id, cachedShopJson);
            return JSONUtil.toBean(cachedShopJson, Shop.class);
        }

        // 2.1 ""
        if ("".equals(cachedShopJson)) {
            log.info("缓存穿透解决方案");
            return null;
        }

        String mutexLockKey = LOCK_SHOP_KEY + id;
        Shop shop;

        // 解决缓存击穿，获取互斥锁，引入DC、重试机制，最后释放
        try {
            // 3. otherwise, fix the hotspot invalid.
            boolean getLock = tryLock(mutexLockKey);
            if (!getLock) {
                // 3.1 fail to get the mutex lock, sleep and retry later.
                log.info("线程{} :获取互斥锁失败！", Thread.currentThread().getName());
                Thread.sleep(50);
                queryWithMutexLock(id);
            }

            // 3.2 Double check the cache
            String dcCached = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(dcCached)) {
                return JSONUtil.toBean(dcCached, Shop.class);
            }

            log.info("线程{} :获取互斥锁成功！", Thread.currentThread().getName());
            // 3.3 get the lock, reconstruct the cache. Mimic the scenario that it lasts long.
            shop = getById(id);
            Thread.sleep(200);

            // 4. null, return
            if (shop == null) {
                log.info("数据库中没有店铺 {} 的信息", id);
                // 4.1 to avoid the cache penetration,
                // add empty to redis with a 2-min ttl when we cannot access the info from database.
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 5. not null, add to cache for 30 min
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            // 6. unlock the mutex lock
            unlock(mutexLockKey);
        }

        // 7. return.
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 查询：解决缓存击穿/热点失效
     * 方案二：逻辑过期。
     * 先预热。
     */
    private Shop queryWithLogicExpiration(Long id) {
        // 1. query from cache.
        // 用hash和string类型都可以。value无需修改，用string也好。
        String key = CACHE_SHOP_KEY + id;
        String cachedShopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. null, return 因为已经缓存提前预热了
        if (StrUtil.isBlank(cachedShopJson)) {
            log.info("没有店铺 {} 的信息", id);
            return null;
        }

        // 3 not null
        // 3.1 check if logic expires
        RedisData redisData = JSONUtil.toBean(cachedShopJson, RedisData.class);

        JSONObject jsonObjectShop = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObjectShop, Shop.class);
        // 3.2 not expire, return Shop
        if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
            return shop;
        }

        // 4. expire, try to get lock
        String lockKey = LOCK_SHOP_KEY + id;
        boolean getLock = tryLock(lockKey);

        // 4.1 got lock
        if (getLock) {
            // 4.2 double check
            String dcCachedShopJson = stringRedisTemplate.opsForValue().get(key);
            RedisData dcRedisData = JSONUtil.toBean(dcCachedShopJson, RedisData.class);

            if (LocalDateTime.now().isBefore(dcRedisData.getExpireTime())) {
                log.info("DC检测到已有缓存，直接返回");
                return JSONUtil.toBean((JSONObject) dcRedisData.getData(), Shop.class);
            }

            // 4.3 rebuilt cache
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    log.info("重建缓存");
                    this.saveShop2Redis(id, 20L); // 为了方便测试，设置为20s有效期
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 4.1 fail to get lock, return Shop (May decrease consistency but increase availability.)
        return shop;
    }

    /**
     * 查询：解决缓存穿透
     */
    private Shop queryWithoutCachePenetration(Long id) {
        // 1. query from cache.
        // 用hash和string类型都可以。value无需修改，用string也好。
        String key = CACHE_SHOP_KEY + id;
        String cachedShopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. not null, return
        if (StrUtil.isNotBlank(cachedShopJson)) {
            log.info("成功从缓存中获取到了店铺 {} 的信息：{}", id, cachedShopJson);
            return JSONUtil.toBean(cachedShopJson, Shop.class);
        }

        // 2.1 ""
        if ("".equals(cachedShopJson)) {
            log.info("缓存穿透解决方案");
            return null;
        }

        // 3. otherwise, query from database
        Shop shop = getById(id);

        // 4. null, return fail
        if (shop == null) {
            log.info("数据库中没有店铺 {} 的信息", id);
            // 4.1 to avoid the cache penetration,
            // add empty to redis with a 2-min ttl when we cannot access the info from database.
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        // 5. not null, add to cache for 30 min
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6. return ok.
        return shop;
    }

    /**
     * 缓存击穿解决：互斥锁上锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 包装类可能为null，调用BooleanUtil.isTrue进行拆箱避免空指针异常。
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional // 单体系统直接用事务。
    public Result update(Shop shop) {
        // verify the id of shop
        if (shop.getId() == null) {
            return Result.fail("店铺ID不能为空");
        }

        // update database
        super.updateById(shop);

        // delete cache
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * 解决缓存击穿方案二：逻辑过期
     * @param id 商铺id
     * @param ttl 过期时间
     */
    public void saveShop2Redis(long id, long ttl) throws InterruptedException {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        redisData.setData(getById(id));

        Thread.sleep(100);
        // 逻辑过期
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
