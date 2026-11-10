-- 本迁移之前的历史订单都来自尚无微信支付的版本。
ALTER TABLE `orders` ADD COLUMN `payment_channel` VARCHAR(16) NOT NULL DEFAULT 'MOCK'
    COMMENT '实际支付渠道：MOCK或WECHAT' AFTER `pay_status`;
