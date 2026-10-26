-- 智能园区餐饮 Agent 平台：当前学习版建表脚本。
-- 参考苍穹外卖的 11 张表，另加 shop、agent_action，共 13 张表。
-- 数据库目标 MySQL 8；说明见 docs/DATABASE_DESIGN.md。
-- 旧版 24 表架构保存在 temp，暂不使用，不能与本脚本混合执行。
-- 只供手动首次建表，无 DROP、业务数据 INSERT 或自动导入。
-- 已存在同名表会报错；这是建表文件，不是旧表迁移文件。

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `campus_dining`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `campus_dining`;

-- 1. 食堂运营账号；不是员工端点餐账号。
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '运营账号主键',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `password` varchar(255) NOT NULL COMMENT '密码哈希，不保存明文',
  `phone` varchar(32) NOT NULL COMMENT '手机号',
  `status` int NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建的运营账号ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改的运营账号ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_employee_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='食堂运营账号';

-- 2. 分类，保留课程字段和类型。
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int NOT NULL COMMENT '1菜品分类，2套餐分类',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `status` int NOT NULL DEFAULT 1 COMMENT '1启用，0禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品及套餐分类';

-- 3. 菜品，课程表上补充 stock/tags/art 对应现有前端。
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据库菜品ID，前端商品ID为id乘2',
  `name` varchar(64) NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类ID，逻辑关联category',
  `price` decimal(10,2) NOT NULL COMMENT '当前单价，元',
  `image` varchar(255) DEFAULT NULL COMMENT '图片地址，当前前端可不使用',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '描述',
  `status` int NOT NULL DEFAULT 0 COMMENT '1起售，0停售',
  `stock` int NOT NULL DEFAULT 0 COMMENT '剩余可售份数，已扣除待支付预占',
  `tags` varchar(255) NOT NULL DEFAULT '[]' COMMENT '标签JSON数组字符串，如["不辣","清淡"]',
  `art` varchar(16) NOT NULL DEFAULT 'chicken' COMMENT '前端插画类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_name` (`name`),
  KEY `idx_dish_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品';

-- 4. 菜品可选口味，课程保留字段；不同于商品固定tags。
CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dish_id` bigint NOT NULL COMMENT '数据库菜品ID',
  `name` varchar(32) NOT NULL COMMENT '选项名，如辣度',
  `value` varchar(255) NOT NULL COMMENT '选项JSON数组字符串，如["不辣","微辣"]',
  PRIMARY KEY (`id`),
  KEY `idx_flavor_dish` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品口味选项';

-- 5. 套餐，保留单独的表，方便跟随课程写CRUD。
CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据库套餐ID，前端商品ID为id乘2加1',
  `category_id` bigint NOT NULL COMMENT '套餐分类ID，逻辑关联category',
  `name` varchar(64) NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '当前套餐单价，元',
  `status` int NOT NULL DEFAULT 0 COMMENT '1起售，0停售',
  `description` varchar(500) NOT NULL DEFAULT '',
  `image` varchar(255) DEFAULT NULL,
  `stock` int NOT NULL DEFAULT 0 COMMENT '套餐自身剩余可售量，另受组成菜品限制',
  `tags` varchar(255) NOT NULL DEFAULT '[]' COMMENT '固定标签JSON数组字符串',
  `art` varchar(16) NOT NULL DEFAULT 'chicken',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_name` (`name`),
  KEY `idx_setmeal_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐';

-- 6. 套餐与菜品关系，copies对应接口composition.quantity。
CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setmeal_id` bigint NOT NULL COMMENT '数据库套餐ID',
  `dish_id` bigint NOT NULL COMMENT '数据库菜品ID',
  `name` varchar(64) NOT NULL COMMENT '关联时菜品名称，课程冗余字段',
  `price` decimal(10,2) NOT NULL COMMENT '关联时菜品单价，不是套餐售价',
  `copies` int NOT NULL DEFAULT 1 COMMENT '每份套餐包含的菜品份数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_dish` (`setmeal_id`, `dish_id`),
  KEY `idx_setmeal_dish_reverse` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐菜品关系';

-- 7. 园区员工点餐账户；现有Web前端使用用户名密码登录。
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `password` varchar(255) NOT NULL COMMENT '密码哈希，不保存明文',
  `name` varchar(64) NOT NULL COMMENT '员工姓名',
  `phone` varchar(32) NOT NULL COMMENT '手机号',
  `status` int NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='园区员工点餐账号';

-- 8. 园区地址用楼栋和详细位置，省市区划暂不需要。
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '点餐员工ID',
  `consignee` varchar(64) NOT NULL COMMENT '收餐人',
  `phone` varchar(32) NOT NULL,
  `building` varchar(100) NOT NULL COMMENT '园区楼栋',
  `detail` varchar(255) NOT NULL COMMENT '楼层、房间等',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '1默认，0非默认；由Service保证每人最多一个',
  PRIMARY KEY (`id`),
  KEY `idx_address_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工收餐地址';

-- 9. 一行一个购物车商品，dish_id和setmeal_id必须且只能填写一个。
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '显示名称，提交订单时重新查询',
  `image` varchar(255) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `dish_id` bigint DEFAULT NULL COMMENT '单品时填写数据库菜品ID',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐时填写数据库套餐ID',
  `dish_flavor` varchar(255) DEFAULT NULL COMMENT '课程口味选择，当前前端未提供该能力',
  `number` int NOT NULL DEFAULT 1 COMMENT '数量，对应接口quantity',
  `amount` decimal(10,2) NOT NULL COMMENT '显示用单价，不是最终成交价格',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cart_user_dish` (`user_id`, `dish_id`),
  UNIQUE KEY `idx_cart_user_setmeal` (`user_id`, `setmeal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车商品';

-- 10. 课程订单表上保留园区所需的自提、截止时间及少量快照字段。
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `number` varchar(50) NOT NULL COMMENT '唯一业务订单号',
  `status` int NOT NULL DEFAULT 1 COMMENT '1待支付，2制作中，3待配送，4配送中，5已完成，6已取消，7待自提',
  `user_id` bigint NOT NULL COMMENT '点餐员工ID',
  `address_book_id` bigint DEFAULT NULL COMMENT '配送时本人地址ID，自提为空；历史展示以快照为准',
  `fulfillment` varchar(16) NOT NULL COMMENT 'DELIVERY配送，PICKUP自提',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `payment_deadline` datetime NOT NULL COMMENT '支付截止时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '成功付款时间',
  `pay_status` tinyint NOT NULL DEFAULT 0 COMMENT '0未支付，1已支付，2已退款；当前为学习模拟流程',
  `amount` decimal(10,2) NOT NULL COMMENT '成交总额，元',
  `remark` varchar(500) NOT NULL DEFAULT '',
  `phone` varchar(32) NOT NULL COMMENT '收餐电话快照',
  `address` varchar(400) NOT NULL COMMENT '配送地址或自提点快照',
  `user_name` varchar(64) NOT NULL COMMENT '员工姓名快照',
  `consignee` varchar(64) NOT NULL COMMENT '收餐人快照',
  `cancel_reason` varchar(255) DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `inventory_snapshot` text NOT NULL COMMENT '下单扣减库存清单JSON字符串，仅后端创建，用于准确释放',
  `events` text NOT NULL COMMENT '订单事件JSON数组字符串，同事务追加，不是模型推理',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_order_number` (`number`),
  KEY `idx_order_user_time` (`user_id`, `order_time`),
  KEY `idx_order_timeout` (`status`, `payment_deadline`),
  KEY `idx_order_time` (`order_time`),
  KEY `idx_order_checkout` (`checkout_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单';

-- 11. 名称、单价、数量都是成交快照，不受当前商品修改影响。
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '成交商品名',
  `image` varchar(255) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `dish_id` bigint DEFAULT NULL COMMENT '数据库菜品ID，与setmeal_id只能填一个',
  `setmeal_id` bigint DEFAULT NULL COMMENT '数据库套餐ID',
  `dish_flavor` varchar(255) DEFAULT NULL,
  `number` int NOT NULL COMMENT '成交数量',
  `amount` decimal(10,2) NOT NULL COMMENT '成交单价，明细小计为amount乘number',
  PRIMARY KEY (`id`),
  KEY `idx_detail_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

-- 12. 当前前端的营业设置，只需一条id=1记录，由业务初始化写入。
CREATE TABLE `shop` (
  `id` bigint NOT NULL COMMENT '单食堂固定填写1',
  `name` varchar(64) NOT NULL COMMENT '食堂名称',
  `status` int NOT NULL DEFAULT 0 COMMENT '1营业，0打烊',
  `opening_hours` varchar(255) NOT NULL COMMENT '展示用营业时间',
  `pickup_point` varchar(255) NOT NULL COMMENT '自提位置',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营业设置';

-- 13. 学到Agent时再使用；对话放Redis，写操作确认保留最小持久记录。
CREATE TABLE `agent_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_id` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全随机生成的公开确认ID',
  `actor_id` bigint NOT NULL COMMENT '操作人ID，结合actor_role解释',
  `actor_role` varchar(16) NOT NULL COMMENT 'user来自user表，admin来自employee表',
  `session_id` varchar(64) NOT NULL COMMENT '服务端分配且绑定身份的会话ID',
  `tool_name` varchar(64) NOT NULL COMMENT '白名单写工具名称',
  `params` text NOT NULL COMMENT '已校验参数JSON字符串，包含数据库目标ID及预期状态，生成后不可改',
  `label` varchar(150) NOT NULL COMMENT '确认卡片标题',
  `detail` varchar(1000) NOT NULL COMMENT '确认卡片影响说明',
  `status` int NOT NULL DEFAULT 0 COMMENT '0待确认，1成功，2撤销，3过期，4失败',
  `result` text DEFAULT NULL COMMENT '成功后的安全content文本，重复确认返回它',
  `expire_time` datetime NOT NULL,
  `confirm_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_action_public_id` (`action_id`),
  KEY `idx_action_owner` (`actor_role`, `actor_id`),
  KEY `idx_action_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent写操作确认；后期学习使用';
