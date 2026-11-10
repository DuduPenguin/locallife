package com.gao.locallife.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gao.locallife.cache.CacheClient;
import com.gao.locallife.entity.Shop;
import com.gao.locallife.entity.ShopType;
import com.gao.locallife.mapper.ShopMapper;
import com.gao.locallife.mapper.ShopTypeMapper;
import com.gao.locallife.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.gao.locallife.common.RedisConstants.CACHE_SHOP_KEY;
import static com.gao.locallife.common.RedisConstants.CACHE_SHOP_TTL;
import static com.gao.locallife.common.RedisConstants.LOCK_SHOP_KEY;

/** 商户查询：普通数据走空值防穿透，热数据走逻辑过期。 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final CacheClient cacheClient;

    @Override
    public Shop queryById(Long id) {
        return cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class,
                shopMapper::selectById,          // 把查库逻辑当参数传进去
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    @Override
    public Shop queryByIdWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY + "logical:", LOCK_SHOP_KEY + "logical:", id, Shop.class,
                shopMapper::selectById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    @Override
    public List<ShopType> queryTypeList() {
        return shopTypeMapper.selectList(
                new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
    }

    @Override
    public List<Shop> queryByType(Long typeId, Integer current) {
        Page<Shop> page = shopMapper.selectPage(
                new Page<>(current == null ? 1 : current, 5),
                new LambdaQueryWrapper<Shop>().eq(Shop::getTypeId, typeId)
                        .orderByDesc(Shop::getScore));
        return page.getRecords();
    }

    /**
     * 逻辑过期的前提是"预热"：key 必须先存在一次，否则读不到。
     *
     * <p>这里用一个简单方法给指定商户写入逻辑过期缓存，启动时和运维接口都会调。
     */
    public void warmUp(List<Long> shopIds) {
        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        Map<Long, Shop> map = shops.stream().collect(Collectors.toMap(Shop::getId, s -> s));
        for (Long id : shopIds) {
            Shop shop = map.get(id);
            if (shop != null) {
                cacheClient.setWithLogicalExpire(
                        CACHE_SHOP_KEY + "logical:" + id, shop, 10L, TimeUnit.SECONDS);
            }
        }
    }
}
