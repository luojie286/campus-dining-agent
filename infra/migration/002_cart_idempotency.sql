-- 仅在目标库中应用一次；此表供购物车增量操作保存成功响应。
CREATE TABLE `cart_idempotency` (
    `user_id` BIGINT NOT NULL,
    `request_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `item_id` BIGINT NOT NULL,
    `quantity` INT NOT NULL,
    `response_json` JSON NOT NULL,
    `expires_at` DATETIME NOT NULL,
    PRIMARY KEY (`user_id`, `request_key`),
    KEY `idx_cart_idempotency_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
