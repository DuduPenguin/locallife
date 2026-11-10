package com.gao.locallife.mq;

import com.gao.locallife.config.OrderDelayConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 发延迟取消消息：订单落库后投一条，30 分钟没支付就会被取消。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendDelay(Long orderId) {
        rabbitTemplate.convertAndSend(
                OrderDelayConfig.ORDER_DELAY_EXCHANGE,
                OrderDelayConfig.ORDER_DELAY_ROUTING_KEY,
                orderId);
        log.debug("已投递延迟取消消息 orderId={}", orderId);
    }
}
