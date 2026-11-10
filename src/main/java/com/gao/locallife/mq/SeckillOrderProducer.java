package com.gao.locallife.mq;

import com.gao.locallife.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 秒杀消息生产者。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(SeckillMessage message) {
        // CorrelationData 里放订单 id，confirm 回调时能知道是哪条消息
        CorrelationData correlation = new CorrelationData(String.valueOf(message.getOrderId()));
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message,
                correlation);
        log.debug("秒杀消息已投递 orderId={}", message.getOrderId());
    }
}
