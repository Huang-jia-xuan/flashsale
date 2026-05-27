-- ============================================================
-- 简易抢购/订单处理系统 —— 建表脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS flash_sale
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE flash_sale;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(64)  NOT NULL                COMMENT '用户名',
    password    VARCHAR(128) NOT NULL                COMMENT '密码',
    nickname    VARCHAR(64)  DEFAULT ''              COMMENT '昵称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 商品表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(128)  NOT NULL                COMMENT '商品名称',
    description TEXT                                  COMMENT '商品描述',
    price       DECIMAL(10,2) NOT NULL                COMMENT '商品单价',
    stock       INT           NOT NULL DEFAULT 0      COMMENT '库存数量',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- -----------------------------------------------------------
-- 3. 订单表
--    索引：idx_user_id 用于按用户查询订单
--    索引：idx_product_id 用于按商品查询订单
--    外键：fk_order_user  -> t_user(id)
--    外键：fk_order_product -> t_product(id)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_order (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no     VARCHAR(32)   NOT NULL                COMMENT '订单编号',
    user_id      BIGINT        NOT NULL                COMMENT '用户ID',
    product_id   BIGINT        NOT NULL                COMMENT '商品ID',
    quantity     INT           NOT NULL                COMMENT '购买数量',
    total_amount DECIMAL(10,2) NOT NULL                COMMENT '订单总金额',
    status       TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态: 0-排队中 1-已完成 2-已失败',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_product_id (product_id),
    CONSTRAINT fk_order_user    FOREIGN KEY (user_id)    REFERENCES t_user(id),
    CONSTRAINT fk_order_product FOREIGN KEY (product_id) REFERENCES t_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- -----------------------------------------------------------
-- 初始测试数据
-- -----------------------------------------------------------
INSERT INTO t_user (username, password, nickname) VALUES
    ('user1', '123456', '测试用户1'),
    ('user2', '123456', '测试用户2'),
    ('user3', '123456', '测试用户3');

INSERT INTO t_product (name, description, price, stock) VALUES
    ('iPhone 15 Pro',  '苹果 iPhone 15 Pro 256GB',           8999.00, 100),
    ('限量款球鞋',      'Air Jordan 1 Retro High OG 限量版',  1299.00,  10),
    ('演唱会门票',      '2024 巡回演唱会 VIP 座位',            1580.00,  50);
