package com.ttnn.flashsale.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单消息体 —— 在 Producer 和 Consumer 之间通过 RabbitMQ 传递
 */
@Data
public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long productId;
    private Integer quantity;
    private Long timestamp;
}
