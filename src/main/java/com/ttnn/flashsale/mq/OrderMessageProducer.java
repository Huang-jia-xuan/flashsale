package com.ttnn.flashsale.mq;

import com.ttnn.flashsale.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 【阶段四 · MQ 生产者】
 * <p>
 * 将下单意向封装为 OrderMessage，发送至 RabbitMQ 的 order.queue 队列。
 */
@Component
@Slf4j
public class OrderMessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void send(OrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                message
        );
        log.info("订单消息已发送: userId={}, productId={}, quantity={}",
                message.getUserId(), message.getProductId(), message.getQuantity());
    }
}
