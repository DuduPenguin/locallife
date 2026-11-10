package com.gao.locallife.service;

/** 秒杀下单。 */
public interface VoucherOrderService {

    /**
     * 【M4-A 同步版，故意有并发问题】先查库存再扣减，并发下会超卖。
     *
     * <p>留着它是为了压测出"超卖 N 件"的对比数据，M4-B 会被 Lua 版替换。
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
}
