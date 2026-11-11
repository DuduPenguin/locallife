package com.gao.locallife.id;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.gao.locallife.common.RedisConstants.ORDER_ID_BEGIN_TIMESTAMP;
import static com.gao.locallife.common.RedisConstants.ORDER_ID_KEY;

/**
 * 订单号生成器：时间戳左移 32 位 | Redis 当天自增。
 *
 * <pre>
 *   高 32 位：当前秒 - 起始秒  -> 单调递增，对 B+ 树友好，不页分裂
 *   低 32 位：Redis 当天 INCR -> 同一秒内不重复
 * </pre>
 *
 * <p>为什么不用 UUID：无序导致页分裂、36 字节太大。
 * <p>为什么不用雪花：依赖时钟，回拨会重复，还要分配 workerId。
 * <p>风险：Redis 丢数据可能重复 —— 靠时间戳跨秒 + 唯一索引兜底。
 */
@Component
@RequiredArgsConstructor
public class RedisIdWorker {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public long nextId(String keyPrefix) {
        // 1. 时间戳部分：直接用 epoch 秒，不掺时区
        //    之前是 LocalDate.now().atStartOfDay().toEpochSecond(UTC) + LocalTime.now()，
        //    日期按本地算、秒数按本地算、但 epoch 按 UTC 算，结果整体偏了 8 小时（东八区），
        //    换机器/换时区时高位还会倒退，破坏单调性。这里改成与机器时区无关的写法。
        long now = System.currentTimeMillis() / 1000;
        long timestamp = now - ORDER_ID_BEGIN_TIMESTAMP;

        // 2. 序列号部分：按天分 key，便于清零和统计日订单量
        String date = LocalDate.now().format(DAY);
        long seq = stringRedisTemplate.opsForValue().increment(ORDER_ID_KEY + keyPrefix + ":" + date);

        // 3. 拼接
        return (timestamp << 32) | seq;
    }
}
