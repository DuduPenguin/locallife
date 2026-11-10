package com.gao.locallife.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀下单消息。
 *
 * <p>只放三个 id，不放整个订单对象 —— 消息体越小越好，
 * 而且消费者应该以数据库为准重新查参数，不信任消息里的金额。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private Long orderId;
    private Long userId;
    private Long voucherId;
}
