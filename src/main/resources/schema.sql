CREATE DATABASE IF NOT EXISTS circuit_breaker_demo DEFAULT CHARACTER SET utf8mb4;

USE circuit_breaker_demo;

CREATE TABLE IF NOT EXISTS `t_order` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`    VARCHAR(32)  NOT NULL COMMENT '订单号',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `amount`      DECIMAL(12,2) NOT NULL COMMENT '金额',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0-待支付 1-已支付 2-已取消',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 初始化测试数据（重复执行幂等）
INSERT IGNORE INTO `t_order` (`id`, `order_no`, `user_id`, `amount`, `status`) VALUES
( 1, 'ORD-20260001', 1001,  99.00, 1),
( 2, 'ORD-20260002', 1002, 299.00, 0),
( 3, 'ORD-20260003', 1001,  59.00, 1),
( 4, 'ORD-20260004', 1003, 199.00, 2),
( 5, 'ORD-20260005', 1002, 399.00, 0),
( 6, 'ORD-20260006', 1001, 129.00, 1),
( 7, 'ORD-20260007', 1004,  49.00, 0),
( 8, 'ORD-20260008', 1003, 599.00, 1),
( 9, 'ORD-20260009', 1002,  79.00, 2),
(10, 'ORD-20260010', 1001, 249.00, 0),
(11, 'ORD-20260011', 1004, 159.00, 1),
(12, 'ORD-20260012', 1003,  39.00, 0),
(13, 'ORD-20260013', 1002, 889.00, 1),
(14, 'ORD-20260014', 1001, 319.00, 2),
(15, 'ORD-20260015', 1004, 499.00, 0);
