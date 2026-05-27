package com.ttnn.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttnn.flashsale.common.Result;
import com.ttnn.flashsale.dto.OrderCreateRequest;
import com.ttnn.flashsale.entity.Order;
import com.ttnn.flashsale.entity.Product;
import com.ttnn.flashsale.exception.BizException;
import com.ttnn.flashsale.lock.RedisDistributedLock;
import com.ttnn.flashsale.mapper.OrderMapper;
import com.ttnn.flashsale.mapper.ProductMapper;
import com.ttnn.flashsale.mq.OrderMessage;
import com.ttnn.flashsale.mq.OrderMessageProducer;
import com.ttnn.flashsale.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现
 * <p>
 * 【阶段一 · 事务】扣减库存与生成订单在同一事务中，保证原子性。<br/>
 * 【阶段三 · 分布式锁】通过 Redis 分布式锁防止并发超卖。<br/>
 * 【阶段四 · MQ】异步下单通过 RabbitMQ 消息队列实现削峰。
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String LOCK_KEY_PREFIX = "lock:product:";
    private static final long LOCK_TIMEOUT_SECONDS = 10;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisDistributedLock distributedLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ================================================================
    // 【阶段一 / 阶段三】同步下单 —— 分布式锁 + 编程式事务
    // ================================================================
    @Override
    public Result<?> createOrderSync(Long userId, OrderCreateRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();

        String lockKey = LOCK_KEY_PREFIX + productId;
        String lockValue = UUID.randomUUID().toString();

        // ① 获取分布式锁
        boolean locked = distributedLock.tryLock(lockKey, lockValue, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!locked) {
            return Result.fail(429, "系统繁忙，请稍后重试");
        }

        try {
            // ② 在事务中完成「扣减库存 + 创建订单」
            Order order = transactionTemplate.execute(status -> {
                int rows = productMapper.deductStock(productId, quantity);
                if (rows == 0) {
                    status.setRollbackOnly();
                    return null;
                }

                Product product = productMapper.selectById(productId);
                Order o = buildOrder(userId, product, quantity);
                orderMapper.insert(o);
                return o;
            });

            if (order == null) {
                return Result.fail(400, "库存不足");
            }

            // ③ 清除商品详情缓存，保证下次读取到最新库存
            redisTemplate.delete(PRODUCT_CACHE_PREFIX + productId);

            return Result.success("下单成功", order);
        } finally {
            distributedLock.unlock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 【阶段四】异步下单 —— 发送 MQ 消息，立即返回"排队中"
    // ================================================================
    @Override
    public Result<?> createOrderAsync(Long userId, OrderCreateRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            return Result.fail(404, "商品不存在");
        }
        if (product.getStock() < request.getQuantity()) {
            return Result.fail(400, "库存不足");
        }

        OrderMessage message = new OrderMessage();
        message.setUserId(userId);
        message.setProductId(request.getProductId());
        message.setQuantity(request.getQuantity());
        message.setTimestamp(System.currentTimeMillis());

        orderMessageProducer.send(message);
        log.info("下单消息已发送至队列: userId={}, productId={}", userId, request.getProductId());

        return Result.success("下单请求已提交，排队处理中", null);
    }

    // ================================================================
    // 【阶段四】MQ 消费者回调 —— 真正执行扣库存 + 生成订单
    // ================================================================
    @Override
    public void processOrderMessage(OrderMessage message) {
        String lockKey = LOCK_KEY_PREFIX + message.getProductId();
        String lockValue = UUID.randomUUID().toString();

        boolean locked = distributedLock.tryLock(lockKey, lockValue, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!locked) {
            throw new BizException("获取锁失败，消息将被重试");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                int rows = productMapper.deductStock(message.getProductId(), message.getQuantity());
                if (rows == 0) {
                    log.warn("库存不足，订单处理跳过: userId={}, productId={}",
                            message.getUserId(), message.getProductId());
                    return;
                }

                Product product = productMapper.selectById(message.getProductId());
                Order order = buildOrder(message.getUserId(), product, message.getQuantity());
                orderMapper.insert(order);
                log.info("异步订单处理成功: orderNo={}", order.getOrderNo());
            });

            redisTemplate.delete(PRODUCT_CACHE_PREFIX + message.getProductId());
        } finally {
            distributedLock.unlock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 查询用户订单列表
    // ================================================================
    @Override
    public List<Order> getUserOrders(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreateTime)
        );
    }

    // ======================== 私有方法 ==============================

    private static final String PRODUCT_CACHE_PREFIX = "product:detail:";

    private Order buildOrder(Long userId, Product product, Integer quantity) {
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(product.getId());
        order.setQuantity(quantity);
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(1); // 已完成
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
