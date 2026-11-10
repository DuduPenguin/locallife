package com.gao.locallife.mq;

import com.gao.locallife.config.RabbitMQConfig;
import com.gao.locallife.service.VoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单消费者：手动 ACK + 三层幂等 + 失败进死信。
 *
 * <p>三条纪律：
 * <ol>
 *   <li><b>业务成功才 ack</b>，不能自动 ack（否则消息丢了业务也没了）</li>
 *   <li><b>失败 nack 且 requeue=false</b>，让死信队列接住；用 requeue=true 会让毒消息无限重投堵死队列</li>
 *   <li><b>唯一索引冲突当成"已处理"</b>，也 ack</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final VoucherOrderService voucherOrderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void onMessage(SeckillMessage msg, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            voucherOrderService.createVoucherOrder(msg.getOrderId(), msg.getUserId(), msg.getVoucherId());
            channel.basicAck(tag, false);
            log.info("订单落库成功 orderId={}", msg.getOrderId());
        } catch (DuplicateKeyException e) {
            // 唯一索引挡住 = 这条已经处理过，当成成功
            channel.basicAck(tag, false);
            log.info("订单重复，忽略 orderId={}", msg.getOrderId());
        } catch (Exception e) {
            // 不重回，交给死信队列；正常项目里还会加告警
            channel.basicNack(tag, false, false);
            log.error("订单落库失败，已转入死信 orderId={}", msg.getOrderId(), e);
        }
    }

    /**
     * 死信消费者：只做记录和告警，不做业务补偿。
     *
     * <p>真实项目里这里应该落一张失败表，再由定时任务/人工重放。
     */
    @RabbitListener(queues = RabbitMQConfig.DLX_QUEUE)
    public void onDeadLetter(SeckillMessage msg, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        log.error("【死信】订单需要人工介入: orderId={} userId={} voucherId={}",
                msg.getOrderId(), msg.getUserId(), msg.getVoucherId());
        channel.basicAck(tag, false);
    }
}
