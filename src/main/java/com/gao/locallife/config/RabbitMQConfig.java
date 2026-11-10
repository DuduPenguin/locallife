package com.gao.locallife.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：秒杀交换机 + 订单队列 + 死信队列。
 *
 * <pre>
 *   seckill.exchange --(seckill.order.create)--> seckill.order.queue
 *                                                      |  消费失败 nack(requeue=false)
 *                                                      v
 *                                            seckill.dlx.exchange -> seckill.dlx.queue
 * </pre>
 *
 * <p>队列设了 <b>死信路由</b>，所以消费者 nack 且不重回时，消息会自动进死信队列，
 * 不会无限重投把主队列堵死。
 */
@Configuration
public class RabbitMQConfig {

    /* ---------------- 业务常量 ---------------- */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order.create";

    public static final String DLX_EXCHANGE = "seckill.dlx.exchange";
    public static final String DLX_QUEUE = "seckill.dlx.queue";
    public static final String DLX_ROUTING_KEY = "seckill.dlx";

    /* ---------------- 交换机 ---------------- */

    @Bean
    public DirectExchange seckillExchange() {
        // durable=true，默认就是 true，这里显式写出来方便讲
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /* ---------------- 队列 ---------------- */

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                // 关键：消费失败且 requeue=false 时，投递到死信交换机
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    /* ---------------- 绑定 ---------------- */

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    /* ---------------- 消息体序列化 ---------------- */

    /**
     * 用 JSON 而不是 JDK 序列化：可读、跨语言、不绑包名。
     *
     * <p>Spring Boot 会自动把这个 Bean 装到 RabbitTemplate 和监听容器上。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
