package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
//        Shop shop = queryWithMutex(id);
        //逻辑过期时间解决缓存穿透
        Shop shop = queryWithLogicalExpire(id);
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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    //逻辑过期时间
    private Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.查询redis商库缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)){
            //3. 未命中返回空
            return null;
        }
        //4.命中,需要先把json转换为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop =JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now()))
        //5.1 未过期返回
            return shop;
        //5.2 过期,需要缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取锁是否成功
        if (isLock){
            //TODO
        //6.3 成功,开启新的线程进行缓存重建
            //TODO 还是得double check
            CACHE_REBUILD_EXECUTOR.submit(()-> {
                try {
                    // 缓存重建
                    saveShop2Redis(id,20L);
                } catch (Exception e) {
                   throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }

            }) ;
        }

        //6.4返回过期的数据
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

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(500);
        //2.封装逻辑过期时间
        RedisData redisData =new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
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
