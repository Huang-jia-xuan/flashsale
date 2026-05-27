package com.ttnn.flashsale.service;

import com.ttnn.flashsale.common.Result;
import com.ttnn.flashsale.dto.OrderCreateRequest;
import com.ttnn.flashsale.entity.Order;
import com.ttnn.flashsale.mq.OrderMessage;

import java.util.List;

public interface OrderService {

    /** 【阶段一/三】同步下单 —— 分布式锁 + 数据库事务 */
    Result<?> createOrderSync(Long userId, OrderCreateRequest request);

    /** 【阶段四】异步下单 —— 发送消息到 RabbitMQ */
    Result<?> createOrderAsync(Long userId, OrderCreateRequest request);

    /** 【阶段四】MQ 消费者回调 —— 扣库存 + 生成订单 */
    void processOrderMessage(OrderMessage message);

    /** 查询指定用户的订单列表 */
    List<Order> getUserOrders(Long userId);
}
