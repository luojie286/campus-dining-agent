-- 支持整车最多100种商品时完整保存所有扣减项。
ALTER TABLE `orders` MODIFY `inventory_snapshot` MEDIUMTEXT NOT NULL
    COMMENT '下单扣减库存清单JSON，供准确释放';

CREATE TABLE `order_submit_replay` (
    `user_id` BIGINT NOT NULL,
    `request_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `response_json` JSON NOT NULL,
    `expires_at` DATETIME NOT NULL,
    PRIMARY KEY (`user_id`, `request_key`),
    KEY `idx_order_submit_replay_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
