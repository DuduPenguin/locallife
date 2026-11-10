package com.gao.locallife.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gao.locallife.cache.CacheClient;
import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.entity.Shop;
import com.gao.locallife.entity.ShopType;
import com.gao.locallife.mapper.ShopMapper;
import com.gao.locallife.mapper.ShopTypeMapper;
import com.gao.locallife.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.gao.locallife.common.RedisConstants.CACHE_SHOP_KEY;
import static com.gao.locallife.common.RedisConstants.CACHE_SHOP_TTL;
import static com.gao.locallife.common.RedisConstants.LOCK_SHOP_KEY;

/** 商户查询：普通数据走空值防穿透，热数据走逻辑过期；附近商户走 GEO。 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

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

    /**
     * 附近商户：GEO 底层是 ZSet + GeoHash，score 是 52 位整数，
     * 所以"空间相邻"变成"数值相邻"，排序即按距离。
     */
    @Override
    public List<Shop> queryNearby(Long typeId, Double x, Double y, Integer current, Integer radius) {
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        int page = current == null ? 1 : current;
        int size = 5;
        int limit = page * size;              // GEOSEARCH 没有 offset，只能多取再截断

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedisTemplate.opsForGeo().radius(
                        key,
                        new Circle(new Point(x, y), new Distance(radius == null ? 5000 : radius, RedisGeoCommands.DistanceUnit.METERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(limit));

        List<Long> ids = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
                ids.add(Long.valueOf(r.getContent().getName()));
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        // 截掉前面 (page-1)*size 条，实现翻页
        int from = (page - 1) * size;
        if (from >= ids.size()) {
            return List.of();
        }
        List<Long> pageIds = ids.subList(from, Math.min(from + size, ids.size()));

        List<Shop> shops = shopMapper.selectBatchIds(pageIds);
        // selectBatchIds 不保证顺序，按距离顺序重排
        List<Shop> ordered = new ArrayList<>();
        for (Long id : pageIds) {
            shops.stream().filter(s -> s.getId().equals(id)).findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }
}
