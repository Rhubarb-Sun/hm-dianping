package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_TTL;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // query cache
        String shopTypeListStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST);

        // not null, return ok
        if (StrUtil.isNotBlank(shopTypeListStr)) {
            log.info("成功从缓存中获取到了店铺类型列表的信息：\n{}", shopTypeListStr);
            List<ShopType> cachedShopTypeList = JSONUtil.toList(shopTypeListStr, ShopType.class);
            return Result.ok(cachedShopTypeList);
        }

        // otherwise, query database
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // null, return fail
        if (CollectionUtil.isEmpty(shopTypeList)) {
            log.info("数据库中没有店铺店铺类型列表");
            return Result.fail("没有店铺类型列表");
        }

        // not null, add to cache
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST, JSONUtil.toJsonStr(shopTypeList),
                CACHE_SHOP_LIST_TTL, TimeUnit.MINUTES);

        // return ok.
        return Result.ok(shopTypeList);
    }
}
