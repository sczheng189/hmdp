package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("数据不存在");
        }
        return Result.ok(shop);
    }

    private Shop queryWithMutex(Long id) {
        //1.查询redis商库缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //hutool的json工具类转换为对象
            //3.存在则返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            log.info("从缓存中获取数据:{}", shopJson);
            return shop;
        }
        //判断命中是否为"空值"
        if (shopJson != null) {
            return null;
        }

        //4.不存在则查询数据库 del
        //4.不存在实现缓存重建
        //4.1.获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            //4.2 判断是否获取到锁
            if (!tryLock(lockKey)) {
                //4.3 未获取到锁，等待重试
                Thread.sleep(100);
                log.info("等待重试");
                return queryWithMutex(id);
            }
            //4.4 获取到锁，查询数据库
            //需要进行double check ,有可能获取到锁的时候，缓存已经被其他线程写入
            shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            //4.5 再次判断是否存在
            if (StrUtil.isNotBlank(shopJson)) {
                //hutool的json工具类转换为对象
                //3.存在则返回
                Shop shop1 = JSONUtil.toBean(shopJson, Shop.class);
                log.info("从缓存中获取数据:{}", shopJson);
                //释放锁
                unlock(lockKey);
                return shop1;
            }
            //判断命中是否为"空值"
            if (shopJson != null) {
                unlock(lockKey);
                return null;
            }
            //4.6 查询数据库
            shop = getById(id);

            //5.不存在返回错误
            if (shop == null) {
                //写入空值，防止缓存穿透
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                        RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在则写入缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
            log.info("写入缓存数据:{}", JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
           throw new RuntimeException(e);
        } finally {
        //6.1释放锁
        unlock(lockKey);
        }

        //7.返回数据
        return shop;
    }

    //获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }




    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null) {
            return Result.fail("id不能为空");
        }
        //1.更新数据库
        updateById(shop);

        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());
        log.info("删除缓存数据:{}", shop.getId());
        return Result.ok();
    }
}
