package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopTypeService typeService;

    @Override
    public List<ShopType> redisList() {
        //1.查询redis商库缓存
        //由于存储的是一个列表，所以需要使用list
        List<String> shopList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_LIST_KEY, 0, -1);

        //2.判断是否存在
        if (shopList != null && shopList.size() > 0) {
            //转换成对象
            List<ShopType> shopTypeList = new ArrayList<>();
            for (String shopJson : shopList) {
                //因为取出来是json字符串，所以需要转换为对象
                ShopType shopType = JSONUtil.toBean(shopJson, ShopType.class);
                shopTypeList.add(shopType);
            }
            log.info("从缓存中获取数据:{}", shopTypeList);
            //3.存在则返回
            return shopTypeList;
        }
        //4.不存在则查询数据库
        List<ShopType> shopTypeList=typeService.query().orderByAsc("sort").list();
        //5.不存在返回错误
        if (shopTypeList == null||shopTypeList.size()==0) {
            return null;
        }
        //6.存在则写入缓存
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForList().rightPush(RedisConstants.CACHE_SHOP_LIST_KEY, JSONUtil.toJsonStr(shopType));
        }
        //7.返回数据
        return shopTypeList;
    }
}
