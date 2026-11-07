ALTER TABLE `user`
    MODIFY COLUMN `username` VARCHAR(64) NULL COMMENT '本地登录名',
    MODIFY COLUMN `password` VARCHAR(255) NULL COMMENT '本地密码哈希';

CREATE TABLE `user_wechat_identity` (
                                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                                        `user_id` BIGINT NOT NULL,
                                        `app_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                        `open_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_wechat_app_open_id` (`app_id`, `open_id`),
                                        KEY `idx_wechat_user_id` (`user_id`),
                                        CONSTRAINT `fk_wechat_identity_user`
                                            FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;