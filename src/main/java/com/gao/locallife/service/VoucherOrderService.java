package com.gao.locallife.service;

import java.util.Map;

/** 秒杀下单与订单生命周期。 */
public interface VoucherOrderService {

    /**
     * 【M4-A 同步版，故意有并发问题】先查库存再扣减，并发下会超卖。
     *
     * <p>留着它是为了压测出"超卖 N 件"的对比数据，M4-B 之后不会被线上调用。
     *
     * @return 订单 id
     */
    Long seckillSync(Long voucherId);

    /**
     * 【M4-B Lua + MQ 版】同步链路只做一次 Lua 预扣，落库交给 MQ 消费者。
     *
     * @return 订单 id（订单是异步创建的，前端用这个 id 轮询结果）
     */
    Long seckill(Long voucherId);

    /** 消费者调用：真正落库（幂等 + 条件更新 + 唯一索引）。 */
    void createVoucherOrder(Long orderId, Long userId, Long voucherId);

    /**
     * 支付（模拟）：状态机 1 -> 2，幂等。
     *
     * @return true 本次真的改成已支付；false 说明订单不存在或状态不允许
     */
    boolean payOrder(Long orderId);

    /**
     * 取消订单并回补库存（超时取消、用户主动取消都走这里）。
     *
     * <p>三步都要做：状态机置为已取消、DB 库存 +1、Redis 库存 +1 并移出已购集合。
     * <p>幂等：状态机 UPDATE 带条件 + Redis 回补前先判 SISMEMBER。
     */
    void cancelOrder(Long orderId);

    /** 对账：返回每个券的「DB 库存 / DB 有效订单 / Redis 已购」以及是否一致。 */
    Map<String, Object> reconcile();
}
