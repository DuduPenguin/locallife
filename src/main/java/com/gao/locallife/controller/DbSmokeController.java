package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.entity.ShopType;
import com.gao.locallife.mapper.ShopTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M1 冒烟测试：验证「表 + 实体 + Mapper + MyBatis-Plus 配置」整条链路通。
 *
 * <p>这里直接调 Mapper 是为了最小化验证；M3 会把它挪进 Service 并加缓存。
 *
 * <p>验收：GET /db/shop-types 返回 5 条分类。
 */
@RestController
@RequestMapping("/db")
@RequiredArgsConstructor
public class DbSmokeController {

    private final ShopTypeMapper shopTypeMapper;

    @GetMapping("/shop-types")
    public Result<List<ShopType>> shopTypes() {
        List<ShopType> list = shopTypeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ShopType>()
                        .orderByAsc("sort"));
        return Result.ok(list);
    }
}
