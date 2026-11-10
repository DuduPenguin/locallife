package com.gao.locallife.mq;

import com.gao.locallife.config.OrderDelayConfig;
import com.gao.locallife.service.VoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消消费者：30 分钟未支付就取消并回补库存。
 *
 * <p>取消本身是幂等的（状态机 UPDATE + Redis Lua 判断），
 * 所以即使延迟消息被重复投递，也不会重复回补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelConsumer {

    private final VoucherOrderService voucherOrderService;

    @RabbitListener(queues = OrderDelayConfig.ORDER_CANCEL_QUEUE)
    public void onTimeout(Long orderId, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            voucherOrderService.cancelOrder(orderId);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, false);
            log.error("超时取消失败 orderId={}", orderId, e);
        }
    }
}
