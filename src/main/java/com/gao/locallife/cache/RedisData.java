package com.gao.locallife.cache;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期的值对象：把"过期时间"写进 value 里，key 本身不设 TTL。
 *
 * <p>这样 key 永远不会被 Redis 自动删除，热 key 失效时不会出现"缓存空缺"，
 * 读请求可以继续拿旧值，由一个线程异步重建。
 */
@Data
public class RedisData {

    /** 逻辑过期时间 */
    private LocalDateTime expireTime;

    /** 真实数据（任意类型，序列化成 JSON） */
    private Object data;
}
