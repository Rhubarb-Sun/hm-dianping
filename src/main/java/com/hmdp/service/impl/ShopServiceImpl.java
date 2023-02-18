package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
//        Shop shop = queryWithoutCachePenetration(id);

        // 解决缓存击穿
        Shop shop = queryWithoutHotspotInvalid(id);
        if (shop == null) {
            log.info("商铺不存在！");
            return Result.fail("商铺不存在！");
        }
        // return ok.
        return Result.ok(shop);
    }

    /**
     * 查询：解决缓存击穿/热点失效
     */
    private Shop queryWithoutHotspotInvalid(Long id) {
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
                queryWithoutHotspotInvalid(id);
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
        updateById(shop);

        // delete cache
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
