# 简易抢购/订单处理系统 (Flash-Sale)

一个基于 **Spring Boot 2.7** 的演示项目，覆盖企业级开发中常用的核心技术组件。

---

## 一、项目架构与业务流程

```
┌─────────┐       ┌──────────────┐       ┌────────────┐       ┌───────┐
│  Client  │──────▶│ AuthInterceptor │──────▶│ Controller │──────▶│Service│
└─────────┘       └──────────────┘       └────────────┘       └───┬───┘
      ▲                                       │AOP                │
      │                                  ApiLogAspect        ┌────┴─────┐
      │                                                      │          │
      │           ┌──────────┐◀── 同步路径 ──────────────── MySQL (事务)  │
      │           │  Redis   │                               │          │
      │           │ (缓存/锁) │◀── 分布式锁 ─────────────────┘          │
      │           └──────────┘                                          │
      │                                                                 │
      │           ┌──────────┐◀── 异步路径 ── Producer ────────────────┘
      └───────────│ RabbitMQ │
                  │  Queue   │──── Consumer ──▶ MySQL (事务)
                  └──────────┘
```

### 核心业务流程

| 路径 | 端点 | 说明 |
|------|------|------|
| **同步下单** | `POST /api/orders/sync` | 获取分布式锁 → 数据库事务（扣库存 + 生成订单）→ 释放锁 |
| **异步下单** | `POST /api/orders` | 快速校验 → 发送 MQ 消息 → 返回"排队处理中"；后台 Consumer 消费消息完成落库 |
| 商品列表 | `GET /api/products` | 直接查数据库 |
| 商品详情 | `GET /api/products/{id}` | Redis 缓存优先 → 缓存未命中回源 DB 并写缓存 |
| 用户订单 | `GET /api/orders` | 需携带 `X-User-Id` 请求头 |

---

## 二、数据库设计

建表脚本：[`sql/schema.sql`](sql/schema.sql)

### 表结构

| 表名 | 说明 | 关键索引/约束 |
|------|------|---------------|
| `t_user` | 用户表 | `uk_username`（唯一索引） |
| `t_product` | 商品表 | `idx_name`（普通索引） |
| `t_order` | 订单表 | `uk_order_no`（唯一索引）、`idx_user_id`、`idx_product_id`、外键 `fk_order_user`、`fk_order_product` |

### 索引设计说明

- **`idx_user_id`**：用户查询自己的订单是高频操作，在 `user_id` 上建索引加速 `WHERE user_id = ?` 查询。
- **`idx_product_id`**：按商品维度统计订单量时使用。
- **`uk_order_no`**：订单号全局唯一，防止重复写入。
- 外键 `fk_order_user` / `fk_order_product` 保证引用完整性。

---

## 三、技术对应清单

| 技术点 | 所在类 | 关键方法 / 配置 |
|--------|--------|-----------------|
| **Interceptor（拦截器）** | `com.ttnn.flashsale.interceptor.AuthInterceptor` | `preHandle()` — 校验 `X-User-Id` 请求头 |
| — 拦截器注册 | `com.ttnn.flashsale.config.WebMvcConfig` | `addInterceptors()` — 注册到 `/api/orders/**` |
| **AOP（切面）** | `com.ttnn.flashsale.aspect.ApiLogAspect` | `around()` — 记录入参、出参、耗时 |
| **事务** | `com.ttnn.flashsale.service.impl.OrderServiceImpl` | `createOrderSync()` 中使用 `TransactionTemplate` 编程式事务 |
| **Redis 缓存** | `com.ttnn.flashsale.service.impl.ProductServiceImpl` | `getProductDetail()` — 缓存读取 + 回写 |
| **分布式锁** | `com.ttnn.flashsale.lock.RedisDistributedLock` | `tryLock()` — SETNX；`unlock()` — Lua 脚本原子释放 |
| — 锁的使用 | `OrderServiceImpl` | `createOrderSync()` / `processOrderMessage()` 中加锁保护库存扣减 |
| **MQ 生产者** | `com.ttnn.flashsale.mq.OrderMessageProducer` | `send()` — 发送消息到 `order.exchange` |
| **MQ 消费者** | `com.ttnn.flashsale.mq.OrderMessageConsumer` | `onMessage()` — 监听 `order.queue` 消费消息 |
| — MQ 配置 | `com.ttnn.flashsale.config.RabbitMQConfig` | Exchange / Queue / Binding 声明 + JSON 序列化 |

---

## 四、项目启动与运行指南

### 4.1 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 5.7+ / 8.0 |
| Redis | 6.0+ |
| RabbitMQ | 3.10+（异步下单阶段需要） |

### 4.2 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

### 4.3 修改配置

编辑 `src/main/resources/application.yml`，修改 MySQL / Redis / RabbitMQ 的连接信息。

### 4.4 启动项目

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/flash-sale-1.0.0.jar
```

### 4.5 快速测试

导入 Postman 集合文件 [`postman/flash-sale-api.postman_collection.json`](postman/flash-sale-api.postman_collection.json) 进行测试。

#### 手动 cURL 示例

```bash
# 1. 查询商品列表
curl http://localhost:8080/api/products

# 2. 查询商品详情（走 Redis 缓存）
curl http://localhost:8080/api/products/1

# 3. 同步下单（需 X-User-Id 请求头）
curl -X POST http://localhost:8080/api/orders/sync \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"productId": 1, "quantity": 1}'

# 4. 异步下单（MQ 削峰）
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"productId": 2, "quantity": 1}'

# 5. 查询用户订单
curl -H "X-User-Id: 1" http://localhost:8080/api/orders

# 6. 未登录拦截测试（不带 X-User-Id）
curl -X POST http://localhost:8080/api/orders/sync \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1}'
```

---

## 五、测试场景

| 场景 | 预期结果 |
|------|----------|
| 正常同步下单 | 返回 200 + 订单信息，库存 -1 |
| 正常异步下单 | 返回"排队处理中"，稍后查询订单列表可见新订单 |
| 库存不足 | 返回 400 "库存不足" |
| 未携带 X-User-Id | 返回 401 "未登录" |
| X-User-Id 不存在 | 返回 401 "用户不存在" |
| 并发抢购（10 库存，20 请求） | 最终订单数 ≤ 10，库存不为负数 |

---

## 六、项目结构

```
src/main/java/com/ttnn/flashsale/
├── FlashSaleApplication.java          # 启动类
├── common/
│   └── Result.java                    # 统一响应包装
├── config/
│   ├── JacksonConfig.java             # Jackson 日期序列化
│   ├── MyBatisPlusConfig.java         # MyBatis-Plus 插件
│   ├── RabbitMQConfig.java            # RabbitMQ Exchange/Queue/Binding
│   └── WebMvcConfig.java              # 拦截器注册
├── controller/
│   ├── OrderController.java           # 订单接口
│   └── ProductController.java         # 商品接口
├── dto/
│   └── OrderCreateRequest.java        # 下单请求体
├── entity/
│   ├── Order.java
│   ├── Product.java
│   └── User.java
├── exception/
│   ├── BizException.java              # 自定义业务异常
│   └── GlobalExceptionHandler.java    # 全局异常处理
├── interceptor/
│   └── AuthInterceptor.java           # 用户身份拦截器
├── aspect/
│   └── ApiLogAspect.java              # AOP 接口日志切面
├── lock/
│   └── RedisDistributedLock.java      # Redis 分布式锁
├── mapper/
│   ├── OrderMapper.java
│   ├── ProductMapper.java
│   └── UserMapper.java
├── mq/
│   ├── OrderMessage.java              # MQ 消息体
│   ├── OrderMessageConsumer.java      # MQ 消费者
│   └── OrderMessageProducer.java      # MQ 生产者
└── service/
    ├── OrderService.java
    ├── ProductService.java
    └── impl/
        ├── OrderServiceImpl.java      # 核心业务（事务 + 锁 + MQ）
        └── ProductServiceImpl.java    # 商品查询（缓存）
```
