package com.gao.locallife.id;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.gao.locallife.common.RedisConstants.ORDER_ID_BEGIN_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单号生成器的单测。
 *
 * <p>为什么先测这个：订单号一旦重复或乱序，会造成唯一索引冲突、以及 B+ 树频繁页分裂，
 * 是那种"上线后才发现、但很难补"的问题。
 *
 * <p>这里用 Mockito 模拟 Redis 的 INCR，不依赖真实 Redis —— 保证 CI 里能跑。
 */
@ExtendWith(MockitoExtension.class)
class RedisIdWorkerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisIdWorker idWorker;

    @BeforeEach
    void setUp() {
        idWorker = new RedisIdWorker(stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("连续生成 1000 个订单号：唯一且递增")
    void shouldGenerateUniqueAndIncreasingIds() {
        AtomicLong seq = new AtomicLong();
        when(valueOperations.increment(anyString())).thenAnswer(inv -> seq.incrementAndGet());

        Set<Long> seen = new HashSet<>();
        long previous = 0;
        for (int i = 0; i < 1000; i++) {
            long id = idWorker.nextId("order");
            assertTrue(seen.add(id), "订单号重复了: " + id);
            assertTrue(id > previous, "订单号没有递增: " + id + " <= " + previous);
            previous = id;
        }
    }

    @Test
    @DisplayName("高 32 位是时间戳、低 32 位是当天序列号")
    void shouldPackTimestampAndSequence() {
        AtomicLong seq = new AtomicLong();
        when(valueOperations.increment(anyString())).thenAnswer(inv -> seq.incrementAndGet());

        long before = System.currentTimeMillis() / 1000;
        long id = idWorker.nextId("order");
        long after = System.currentTimeMillis() / 1000;

        long timestampPart = id >>> 32;
        long sequencePart = id & 0xFFFFFFFFL;

        assertEquals(1L, sequencePart, "第一个订单号的序列号应该是 1");
        // 时间戳部分 = now - 起始时间，允许跨秒的 1 秒误差
        long expectedLow = before - ORDER_ID_BEGIN_TIMESTAMP - 1;
        long expectedHigh = after - ORDER_ID_BEGIN_TIMESTAMP + 1;
        assertTrue(timestampPart >= expectedLow && timestampPart <= expectedHigh,
                "时间戳部分不在预期区间: " + timestampPart);
    }

    @Test
    @DisplayName("同一秒内两个订单号：高位相同、低位相差 1")
    void idsWithinSameSecondShouldShareHighBits() {
        AtomicLong seq = new AtomicLong();
        when(valueOperations.increment(anyString())).thenAnswer(inv -> seq.incrementAndGet());

        long first = idWorker.nextId("order");
        long second = idWorker.nextId("order");

        assertNotEquals(first, second);
        assertEquals(1L, (second & 0xFFFFFFFFL) - (first & 0xFFFFFFFFL), "低 32 位应连续");
        long highDiff = (second >>> 32) - (first >>> 32);
        assertTrue(highDiff == 0 || highDiff == 1, "两次调用不应跨越多于一秒");
    }

    @Test
    @DisplayName("按天分 key：Redis key 由前缀 + 业务 + 日期组成")
    void shouldUsePerDayRedisKey() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        idWorker.nextId("order");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith("icr:order:order:"), "key 前缀不对: " + key);
        assertEquals(8, key.substring(key.lastIndexOf(':') + 1).length(), "日期应该是 yyyyMMdd: " + key);
    }
}
