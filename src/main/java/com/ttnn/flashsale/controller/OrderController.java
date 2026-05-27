package com.ttnn.flashsale.controller;

import com.ttnn.flashsale.common.Result;
import com.ttnn.flashsale.dto.OrderCreateRequest;
import com.ttnn.flashsale.entity.Order;
import com.ttnn.flashsale.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 同步下单（阶段一 / 阶段三）
     * <p>直接在数据库中完成扣减库存 + 创建订单，使用分布式锁防超卖。
     */
    @PostMapping("/sync")
    public Result<?> createOrderSync(@Valid @RequestBody OrderCreateRequest request,
                                     HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return orderService.createOrderSync(userId, request);
    }

    /**
     * 异步下单（阶段四）
     * <p>将下单消息发送至 RabbitMQ，立即返回"排队处理中"。
     */
    @PostMapping
    public Result<?> createOrderAsync(@Valid @RequestBody OrderCreateRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return orderService.createOrderAsync(userId, request);
    }

    /** 查询当前用户的订单列表 */
    @GetMapping
    public Result<List<Order>> getUserOrders(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(orderService.getUserOrders(userId));
    }
}
