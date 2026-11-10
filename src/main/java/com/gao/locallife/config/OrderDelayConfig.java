package com.gao.locallife.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时取消的延迟队列（TTL + 死信实现，不依赖 RabbitMQ 插件）。
 *
 * <pre>
 *   order.delay.exchange --> order.delay.queue            (整队 TTL = 30 分钟)
 *                                  |  消息过期后成为死信
 *                                  v
 *                            order.cancel.exchange --> order.cancel.queue
 *                                  |
 *                                  v
 *                           OrderCancelConsumer 取消订单 + 回补库存
 * </pre>
 *
 * <p><b>为什么不用延迟插件</b>：生产环境装插件要运维配合，TTL + DLX 是零依赖方案。
 * <p><b>为什么这里不会有队头阻塞</b>：整队 TTL 是统一值，先进的先过期，FIFO 成立。
 * 如果每条消息 TTL 不同（比如 10 分钟和 30 分钟混用），才会出现"前面不过期、后面也出不去"。
 */
@Configuration
public class OrderDelayConfig {

    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 订单超时时间：30 分钟 */
    public static final int ORDER_TIMEOUT_MILLIS = 30 * 60 * 1000;

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .ttl(ORDER_TIMEOUT_MILLIS)                              // 整队 TTL
                .deadLetterExchange(ORDER_CANCEL_EXCHANGE)              // 过期后转投
                .deadLetterRoutingKey(ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderCancelExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }
}
