CREATE TABLE `order_refund` (
    `order_id` BIGINT NOT NULL,
    `refund_no` VARCHAR(64) NOT NULL,
    `channel` VARCHAR(16) NOT NULL,
    `state` VARCHAR(16) NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `reason` VARCHAR(200) NOT NULL,
    `requested_at` DATETIME NOT NULL,
    `refunded_at` DATETIME NULL,
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_order_refund_number` (`refund_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
