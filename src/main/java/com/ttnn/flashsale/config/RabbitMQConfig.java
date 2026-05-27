package com.ttnn.flashsale.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【阶段四 · RabbitMQ 配置】
 * <p>
 * 定义 Exchange、Queue、Binding，并使用 Jackson 进行消息序列化。
 */
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE    = "order.exchange";
    public static final String ORDER_QUEUE       = "order.queue";
    public static final String ORDER_ROUTING_KEY = "order.create";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE).build();
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(ORDER_ROUTING_KEY);
    }

    /** 使用 JSON 格式序列化/反序列化 MQ 消息体 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
