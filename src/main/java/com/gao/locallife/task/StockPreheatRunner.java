package com.gao.locallife.task;

import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.entity.SeckillVoucher;
import com.gao.locallife.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀库存预热：把 DB 里的秒杀库存写进 Redis。
 *
 * <p>M4-B 的 Lua 脚本读的是 {@code seckill:stock:{voucherId}}，
 * 所以应用启动时必须先同步一次，否则脚本读到 nil 会直接判"售罄"。
 *
 * <p><b>注意</b>：活动进行中不能重新全量预热（会用 DB 值覆盖掉 Redis 里已扣减的库存），
 * 真实项目应该在"活动开始前"做一次，之后靠扣减和回补维护。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockPreheatRunner implements CommandLineRunner {

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        List<SeckillVoucher> list = seckillVoucherMapper.selectList(null);
        for (SeckillVoucher v : list) {
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.SECKILL_STOCK_KEY + v.getVoucherId(), String.valueOf(v.getStock()));
        }
        log.info("秒杀库存预热完成，共 {} 张券: {}", list.size(),
                list.stream().map(v -> v.getVoucherId() + "=" + v.getStock()).toList());
    }
}
