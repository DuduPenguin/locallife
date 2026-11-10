package com.gao.locallife.task;

import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.entity.SeckillVoucher;
import com.gao.locallife.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

/**
 * 秒杀预热：把库存和活动时间窗写进 Redis。
 *
 * <p>M4-B 的 Lua 脚本读 {@code seckill:stock:{voucherId}}（注意有大括号 hash tag），
 * 时间校验读 {@code seckill:time:{voucherId}}，所以启动必须先同步一次。
 *
 * <p><b>注意</b>：活动进行中不能重新全量预热 —— 会用 DB 值覆盖 Redis 里已扣减的库存。
 * 真实项目只在"活动开始前"预热一次，之后靠扣减和回补维护。
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
            Long id = v.getVoucherId();
            // 库存：key 带 hash tag，和 Lua 脚本、业务代码保持一致
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.SECKILL_STOCK_KEY + "{" + id + "}", String.valueOf(v.getStock()));

            // 时间窗：Unix 秒，避免每次下单都查库
            long begin = v.getBeginTime().toEpochSecond(ZoneOffset.ofHours(8));
            long end = v.getEndTime().toEpochSecond(ZoneOffset.ofHours(8));
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.SECKILL_TIME_KEY + id, begin + ":" + end);

            // 清空上一轮的已购集合（重新压测时必须清，否则会被判"重复下单"）
            stringRedisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + "{" + id + "}");
        }
        log.info("秒杀预热完成，共 {} 张券: {}", list.size(),
                list.stream().map(v -> v.getVoucherId() + "=" + v.getStock()).toList());
    }
}
