package com.gao.locallife.task;

import com.gao.locallife.service.impl.ShopServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热：应用启动时把"热商户"写进逻辑过期缓存。
 *
 * <p>为什么必须预热：逻辑过期的 key <b>不设 TTL</b>，靠 value 里的 expireTime 判断。
 * 如果 key 从来没写入过，读请求会直接 miss，逻辑过期那套"不阻塞"就失效了。
 *
 * <p>真实项目里预热的数据来自"访问量 Top N"的离线统计；这里写死 3 个演示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachePreheatRunner implements CommandLineRunner {

    private final ShopServiceImpl shopService;

    @Override
    public void run(String... args) {
        List<Long> hotShopIds = List.of(1L, 2L, 3L);
        try {
            shopService.warmUp(hotShopIds);
            log.info("缓存预热完成，热商户: {}", hotShopIds);
        } catch (Exception e) {
            log.warn("缓存预热失败（不影响启动）: {}", e.getMessage());
        }
    }
}
