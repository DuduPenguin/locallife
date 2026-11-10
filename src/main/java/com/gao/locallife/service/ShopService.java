package com.gao.locallife.service;

import com.gao.locallife.entity.Shop;
import com.gao.locallife.entity.ShopType;

import java.util.List;

/** 商户与分类。 */
public interface ShopService {

    /** 按 id 查商户（走缓存：空值防穿透）。 */
    Shop queryById(Long id);

    /** 按 id 查商户（走缓存：逻辑过期防击穿）。 */
    Shop queryByIdWithLogicalExpire(Long id);

    /** 分类列表。 */
    List<ShopType> queryTypeList();

    /** 按分类分页查商户。 */
    List<Shop> queryByType(Long typeId, Integer current);

    /**
     * 附近商户：Redis GEO，按距离升序。
     *
     * <p>GEOSEARCH 不支持 offset，所以只能取前 page*size 条再截断（深分页要改成距离游标）。
     *
     * @param typeId 分类。GEO 只能按距离筛、不能按分类过滤，所以按分类分 key
     * @param x      经度
     * @param y      纬度
     * @param radius 半径（米）
     */
    List<Shop> queryNearby(Long typeId, Double x, Double y, Integer current, Integer radius);
}
