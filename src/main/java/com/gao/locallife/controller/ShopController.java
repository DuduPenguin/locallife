package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.entity.Shop;
import com.gao.locallife.entity.ShopType;
import com.gao.locallife.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商户与分类（公开接口，已在白名单里）。 */
@RestController
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    /** 商户详情：走空值防穿透。 */
    @GetMapping("/shop/{id}")
    public Result<Shop> queryById(@PathVariable("id") Long id) {
        return Result.ok(shopService.queryById(id));
    }

    /** 商户详情：走逻辑过期（需先预热）。对比用。 */
    @GetMapping("/shop/logical/{id}")
    public Result<Shop> queryByIdLogical(@PathVariable("id") Long id) {
        return Result.ok(shopService.queryByIdWithLogicalExpire(id));
    }

    /** 分类列表。 */
    @GetMapping("/shop-type/list")
    public Result<List<ShopType>> typeList() {
        return Result.ok(shopService.queryTypeList());
    }

    /** 按分类分页查商户。 */
    @GetMapping("/shop/of/type")
    public Result<List<Shop>> ofType(@RequestParam("typeId") Long typeId,
                                    @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return Result.ok(shopService.queryByType(typeId, current));
    }

    /**
     * 附近商户（Redis GEO，按距离升序）。
     *
     * <p>例：/shop/nearby?typeId=1&x=126.632&y=45.741&current=1&radius=5000
     */
    @GetMapping("/shop/nearby")
    public Result<List<Shop>> nearby(@RequestParam("typeId") Long typeId,
                                    @RequestParam("x") Double x,
                                    @RequestParam("y") Double y,
                                    @RequestParam(value = "current", defaultValue = "1") Integer current,
                                    @RequestParam(value = "radius", defaultValue = "5000") Integer radius) {
        return Result.ok(shopService.queryNearby(typeId, x, y, current, radius));
    }
}
