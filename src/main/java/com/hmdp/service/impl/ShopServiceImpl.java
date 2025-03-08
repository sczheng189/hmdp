package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //1.查询redis商库缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TTL + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //hutool的json工具类转换为对象
            //3.存在则返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            log.info("从缓存中获取数据:{}", shopJson);
            return Result.ok(shop);
        }

        //4.不存在则查询数据库
        Shop shop = getById(id);

        //5.不存在返回错误
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        //6.存在则写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        log.info("写入缓存数据:{}", JSONUtil.toJsonStr(shop));
        //7.返回数据
        return Result.ok(shop);
    }
}
