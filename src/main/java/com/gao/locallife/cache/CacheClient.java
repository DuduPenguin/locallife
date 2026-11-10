package com.gao.locallife.cache;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用缓存组件：把"查库逻辑"作为参数传进来，组件本身不依赖任何 Mapper。
 *
 * <p>三个方法对应三种问题：
 * <ul>
 *   <li>{@link #queryWithPassThrough} 空值缓存防<b>穿透</b></li>
 *   <li>{@link #queryWithMutex} 互斥锁防<b>击穿</b>（强一致，会阻塞）</li>
 *   <li>{@link #queryWithLogicalExpire} 逻辑过期防<b>击穿</b>（高可用，读旧值）</li>
 * </ul>
 *
 * <p>为什么用 Jackson 而不是 Hutool 的 JSONUtil：实体里有 LocalDateTime，
 * Spring 自动配置的 ObjectMapper 已经装了 JavaTimeModule，序列化/反序列化不会踩坑。
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 逻辑过期重建用的线程池（重建失败不影响读请求） */
    private static final ExecutorService REBUILD_POOL = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /* ==================== 基础写 ==================== */

    /** 普通写入（带 TTL） */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), time, unit);
        } catch (Exception e) {
            throw new IllegalStateException("缓存写入失败: " + key, e);
        }
    }

    /** 逻辑过期写入：<b>不设 TTL</b>，过期时间放进 value */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData data = new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            throw new IllegalStateException("逻辑过期缓存写入失败: " + key, e);
        }
    }

    /* ==================== 三种查询 ==================== */

    /**
     * 缓存穿透防护：查不到就写空串（短 TTL），下次直接返回 null。
     *
     * @param keyPrefix  key 前缀，如 "cache:shop:"
     * @param id         业务主键
     * @param type       返回类型（泛型擦除，必须显式传）
     * @param dbFallback 查库逻辑
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        String json = redis.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return read(json, type);
        }
        // 空串代表"库里也没有"，直接返回，避免每次都打到 DB
        if (json != null) {
            return null;
        }

        R r = dbFallback.apply(id);
        if (r == null) {
            redis.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
            return null;
        }
        // TTL 加随机值，防雪崩
        long ttl = unit.toSeconds(time) + ThreadLocalRandom.current().nextLong(0, 300);
        set(key, r, ttl, TimeUnit.SECONDS);
        return r;
    }

    /**
     * 缓存击穿（互斥锁版）：只让一个线程重建，其他线程等待。
     *
     * <p>适合不能读旧值的数据（价格、库存）。
     */
    public <R, ID> R queryWithMutex(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                    Function<ID, R> dbFallback,
                                    Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        String json = redis.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return read(json, type);
        }
        if (json != null) {
            return null;
        }

        String lockKey = lockPrefix + id;
        R r;
        try {
            // 拿锁失败就自旋重试（模拟等待重建完成）
            if (!tryLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, lockPrefix, id, type, dbFallback, time, unit);
            }
            // 双检：可能在等锁期间别人已经重建好了
            json = redis.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return read(json, type);
            }
            r = dbFallback.apply(id);
            if (r == null) {
                redis.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
                return null;
            }
            long ttl = unit.toSeconds(time) + ThreadLocalRandom.current().nextLong(0, 300);
            set(key, r, ttl, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("重建缓存被中断: " + key, e);
        } finally {
            unlock(lockKey);
        }
        return r;
    }

    /**
     * 缓存击穿（逻辑过期版）：key 不设 TTL，过期后由一个线程异步重建，其他线程返回旧值。
     *
     * <p>适合能短暂读旧值的数据（商户信息、文章详情）——<b>宁可看到几秒前的地址，也不能让首页转圈</b>。
     *
     * <p>前提：数据必须<b>预热</b>（先写入过一次），否则 key 不存在会直接返回 null。
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        String json = redis.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;                      // 没预热：交给上层决定要不要回源
        }

        RedisData redisData = readRedisData(json);
        R r = objectMapper.convertValue(redisData.getData(), type);

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;                         // 未过期，直接返回
        }

        String lockKey = lockPrefix + id;
        if (tryLock(lockKey)) {
            // 拿到锁：异步重建，当前请求先返回旧值（不阻塞）
            REBUILD_POOL.submit(() -> {
                try {
                    R fresh = dbFallback.apply(id);
                    setWithLogicalExpire(key, fresh, time, unit);
                    log.debug("逻辑过期缓存重建完成: {}", key);
                } catch (Exception e) {
                    log.error("逻辑过期缓存重建失败: {}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 没抢到锁也直接返回旧值（这就是"不阻塞"的含义）
        return r;
    }

    /* ==================== 内部工具 ==================== */

    private <R> R read(String json, Class<R> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    private RedisData readRedisData(String json) {
        try {
            return objectMapper.readValue(json, RedisData.class);
        } catch (Exception e) {
            throw new IllegalStateException("逻辑过期缓存反序列化失败", e);
        }
    }

    private boolean tryLock(String lockKey) {
        Boolean ok = redis.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String lockKey) {
        redis.delete(lockKey);
    }
}
