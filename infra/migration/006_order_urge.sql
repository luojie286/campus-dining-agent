-- 客户催单计数：一次订单一行，冷却与总次数由 OrderUrgeService 在同一事务内校验。
CREATE TABLE `order_urge` (
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `urge_count` INT NOT NULL DEFAULT 0 COMMENT '累计催单次数，最多5次',
    `last_urge_at` DATETIME NULL COMMENT '最后一次成功催单时间，用于60秒冷却',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
