package com.gao.locallife.common;

/**
 * 全部 Redis key 常量。
 *
 * <p>命名规范：业务:对象:标识（冒号分层，便于 SCAN 与监控）。
 * <p>这里集中管理，避免各处硬编码导致 key 不一致。
 */
public final class RedisConstants {

    private RedisConstants() {
    }

    /* ============ 登录（M2） ============ */
    /** 验证码：login:code:{phone}，TTL 120s */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final long LOGIN_CODE_TTL = 120L;

    /** 登录态：login:token:{token}，Hash，TTL 1800s */
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    public static final long LOGIN_TOKEN_TTL = 1800L;

    /* ============ 缓存（M3） ============ */
    /** 商户缓存：cache:shop:{id}，TTL 1800s + 随机 0~300s */
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_TTL_UNIT = "MINUTES";

    /** 缓存空值 TTL：2 分钟 */
    public static final long CACHE_NULL_TTL = 2L;

    /** 重建锁：lock:shop:{id}，TTL 10s */
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final long LOCK_SHOP_TTL = 10L;

    /* ============ 秒杀（M4） ============ */
    /** 秒杀库存：seckill:stock:{voucherId} */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 已购集合：seckill:order:{voucherId}，Set，member = userId */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    /** 活动时间窗：seckill:time:{voucherId}，value = "beginEpoch:endEpoch" */
    public static final String SECKILL_TIME_KEY = "seckill:time:";

    /* ============ 订单（M5） ============ */
    /** 用户下单锁：lock:order:{userId} */
    public static final String LOCK_ORDER_KEY = "lock:order:";
    /** 订单号自增：icr:order:{yyyyMMdd} */
    public static final String ORDER_ID_KEY = "icr:order:";
    /** 订单号起始时间戳（2025-01-01 00:00:00 UTC） */
    public static final long ORDER_ID_BEGIN_TIMESTAMP = 1735689600L;

    /* ============ 社交（M6） ============ */
    /** 点赞：like:blog:{blogId}，ZSet(member=userId, score=时间戳) */
    public static final String LIKE_BLOG_KEY = "like:blog:";
    /** Feed：feed:{userId}，ZSet(member=blogId, score=发布时间戳) */
    public static final String FEED_KEY = "feed:";
    /** 关注：follow:follows:{userId} */
    public static final String FOLLOW_KEY = "follow:follows:";
    /** 粉丝：follow:fans:{userId} */
    public static final String FANS_KEY = "follow:fans:";
    /** 签到：sign:{userId}:{yyyyMM}，Bitmap */
    public static final String SIGN_KEY = "sign:";

    /* ============ 地理（M7） ============ */
    /** 商户地理：shop:geo:{typeId}，member = shopId */
    public static final String SHOP_GEO_KEY = "shop:geo:";
}
