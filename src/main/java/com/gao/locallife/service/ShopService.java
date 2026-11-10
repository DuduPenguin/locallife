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
}
