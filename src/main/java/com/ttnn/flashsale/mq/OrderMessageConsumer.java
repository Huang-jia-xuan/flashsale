package com.ttnn.flashsale.mq;

import com.ttnn.flashsale.config.RabbitMQConfig;
import com.ttnn.flashsale.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 【阶段四 · MQ 消费者】
 * <p>
 * 监听 order.queue 队列，依次取出下单消息，调用 OrderService 完成扣库存 + 生成订单。
 */
@Component
@Slf4j
public class OrderMessageConsumer {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onMessage(OrderMessage message) {
        log.info("收到订单消息: userId={}, productId={}, quantity={}",
                message.getUserId(), message.getProductId(), message.getQuantity());
        try {
            orderService.processOrderMessage(message);
        } catch (Exception e) {
            log.error("订单消息处理失败: {}", e.getMessage(), e);
            throw e; // 抛出异常触发 RabbitMQ 重试机制
        }
    }
}
