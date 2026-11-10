package com.gao.locallife.task;

import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.entity.Shop;
import com.gao.locallife.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商户地理坐标预热：把 DB 里的经纬度写进 Redis GEO。
 *
 * <pre>
 *   key:    shop:geo:{typeId}
 *   member: shopId
 *   score:  GeoHash（52 位整数，由经纬度算出）
 * </pre>
 *
 * <p>按分类分 key 的原因：GEO 只能按距离筛选，不能按分类过滤。
 * 分 key 后每次查询的候选集更小，也省掉了"先查全部再过滤"的开销。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopGeoPreheatRunner implements CommandLineRunner {

    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        List<Shop> shops = shopMapper.selectList(null);
        int written = 0;
        int updated = 0;
        for (Shop shop : shops) {
            if (shop.getX() == null || shop.getY() == null || shop.getTypeId() == null) {
                continue;
            }
            String key = RedisConstants.SHOP_GEO_KEY + shop.getTypeId();
            // add(key, Point(经度, 纬度), member)
            // 注意：GEOADD 对已存在的 member 返回 0，但它仍然会覆盖坐标。
            // 所以不能用返回值统计"写入数"，否则重启时日志永远是 0，看着像失败。
            Long added = stringRedisTemplate.opsForGeo()
                    .add(key, new Point(shop.getX(), shop.getY()), String.valueOf(shop.getId()));
            if (added != null && added > 0) {
                written++;
            } else {
                updated++;
            }
        }
        log.info("商户 GEO 预热完成：新增 {} 个，更新 {} 个（合计 {}）", written, updated, written + updated);
    }
}
