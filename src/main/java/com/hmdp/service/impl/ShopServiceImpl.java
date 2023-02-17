package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
        // 1. query from cache.
        // 用hash和string类型都可以。value无需修改，用string也好。
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. not null, return
        if (StrUtil.isNotBlank(shopJson)) {
            log.info("成功从缓存中获取到了店铺 {} 的信息：{}", id, shopJson);
            Shop cachedShop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(cachedShop);
        }

        // 3. otherwise, query from database
        Shop shop = getById(id);

        // 4. null, return fail
        if (shop == null) {
            log.info("数据库中没有店铺 {} 的信息", id);
            return Result.fail("店铺不存在");
        }

        // 5. not null, add to cache for 30 min
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6. return ok.
        return Result.ok(shop);
    }
}
