-- 智能园区餐饮 Agent 平台：首次建表脚本，设计版本 1.0 / 2026-10-10。
-- 目标 MySQL 8.0.16+，推荐与项目 Compose 一致的 MySQL 8.4。
-- 配套说明：docs/DATABASE_DESIGN.md。共 24 张表，无业务初始化数据。
-- 手动执行；不会修改 Java、前端、连接配置，也不会自动导入数据库。
-- 不含 DROP/TRUNCATE，不关闭外键检查。已有同名表时失败，不能用作升级迁移。
-- DDL 会隐式提交；执行失败时可能已创建前面的表，应检查错误后再处理。

SET NAMES utf8mb4;
SET SESSION time_zone = '+08:00';

CREATE DATABASE IF NOT EXISTS campus_dining
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_dining;

-- 1. 食堂运营账户；与园区员工点餐账户分离。
CREATE TABLE employee (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '运营账户 ID',
  username VARCHAR(64) NOT NULL COMMENT '登录名；应用层统一规范化',
  password_hash VARCHAR(255) NOT NULL COMMENT '带算法及参数的密码哈希，禁止明文',
  name VARCHAR(64) NOT NULL COMMENT '运营人员姓名',
  phone VARCHAR(32) NOT NULL COMMENT '联系电话',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用，1 启用',
  must_change_password TINYINT NOT NULL DEFAULT 1 COMMENT '首次登录需修改初始密码',
  token_version BIGINT NOT NULL DEFAULT 0 COMMENT '令牌撤销版本，需鉴权服务校验',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_username (username),
  CONSTRAINT ck_employee_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_employee_flags CHECK (must_change_password IN (0, 1)),
  CONSTRAINT ck_employee_versions CHECK (token_version >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='食堂运营账户';

-- 2. 园区员工点餐账户。工号不是订单归属的可信输入，归属来自登录上下文。
CREATE TABLE user_account (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '园区员工 ID',
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  staff_no VARCHAR(64) NULL COMMENT '园区工号，可空；填写时唯一',
  name VARCHAR(64) NOT NULL COMMENT '员工姓名',
  phone VARCHAR(32) NOT NULL COMMENT '联系电话',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用，1 启用',
  must_change_password TINYINT NOT NULL DEFAULT 1,
  token_version BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_staff_no (staff_no),
  CONSTRAINT ck_user_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_user_flags CHECK (must_change_password IN (0, 1)),
  CONSTRAINT ck_user_versions CHECK (token_version >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='园区员工点餐账户';

-- 3. 单园区、单食堂配置。首次业务初始化由应用显式创建 id=1 的记录。
CREATE TABLE shop (
  id TINYINT NOT NULL COMMENT '单食堂固定为 1',
  name VARCHAR(100) NOT NULL COMMENT '食堂名称',
  is_open TINYINT NOT NULL DEFAULT 0 COMMENT '0 打烊，1 营业；初始关闭',
  opening_hours VARCHAR(255) NOT NULL COMMENT '展示用营业时间，不是排班引擎',
  pickup_point VARCHAR(255) NOT NULL COMMENT '自提位置',
  version BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NULL COMMENT '最后修改的运营账户',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_shop_employee FOREIGN KEY (updated_by) REFERENCES employee (id),
  CONSTRAINT ck_shop_id CHECK (id = 1),
  CONSTRAINT ck_shop_open CHECK (is_open IN (0, 1)),
  CONSTRAINT ck_shop_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单食堂营业设置';

-- 4. 分类保留课程的 type=1/2，菜单用复合外键保证类型匹配。
CREATE TABLE category (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  type TINYINT NOT NULL COMMENT '1 菜品，2 套餐',
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_id_type (id, type),
  KEY idx_category_list (is_deleted, status, type, sort, id),
  CONSTRAINT fk_category_creator FOREIGN KEY (created_by) REFERENCES employee (id),
  CONSTRAINT fk_category_updater FOREIGN KEY (updated_by) REFERENCES employee (id),
  CONSTRAINT ck_category_type CHECK (type IN (1, 2)),
  CONSTRAINT ck_category_flags CHECK (status IN (0, 1) AND is_deleted IN (0, 1)),
  CONSTRAINT ck_category_deleted CHECK (is_deleted = 0 OR status = 0),
  CONSTRAINT ck_category_values CHECK (sort >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品及套餐分类';

-- 5. 统一商品 ID，避免 dish/setmeal 分表自增 ID 在前端购物车中冲突。
CREATE TABLE menu_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '前端所有 itemId 的统一来源',
  kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'dish / setmeal；创建后不可改',
  category_id BIGINT NOT NULL,
  category_type TINYINT NOT NULL COMMENT 'dish=1，setmeal=2；复合外键辅助字段',
  name VARCHAR(100) NOT NULL,
  price DECIMAL(12,2) NOT NULL COMMENT '当前单价，元',
  description VARCHAR(1000) NOT NULL DEFAULT '',
  art VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'chicken' COMMENT '前端插画键',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 停售，1 起售；创建默认停售',
  stock INT NOT NULL DEFAULT 0 COMMENT '当前可售量；已预占数量已经扣除',
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0 COMMENT '库存和商品变更均递增',
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_id_kind (id, kind),
  KEY idx_menu_category (category_id, category_type, is_deleted, status, id),
  KEY idx_menu_kind_status (kind, is_deleted, status, id),
  CONSTRAINT fk_menu_category FOREIGN KEY (category_id, category_type) REFERENCES category (id, type),
  CONSTRAINT fk_menu_creator FOREIGN KEY (created_by) REFERENCES employee (id),
  CONSTRAINT fk_menu_updater FOREIGN KEY (updated_by) REFERENCES employee (id),
  CONSTRAINT ck_menu_kind CHECK ((kind = 'dish' AND category_type = 1) OR (kind = 'setmeal' AND category_type = 2)),
  CONSTRAINT ck_menu_flags CHECK (status IN (0, 1) AND is_deleted IN (0, 1)),
  CONSTRAINT ck_menu_deleted CHECK (is_deleted = 0 OR status = 0),
  CONSTRAINT ck_menu_values CHECK (price > 0 AND stock >= 0 AND version >= 0),
  CONSTRAINT ck_menu_art CHECK (art IN ('chicken', 'fish', 'beef', 'salad', 'noodle', 'tofu'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一菜品及套餐商品';

-- 6-7. 维护过的结构化标签，不将 LLM 推断写成已验证事实。
CREATE TABLE menu_tag (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定标签编码',
  name VARCHAR(64) NOT NULL COMMENT '接口 tags 中的显示文本',
  tag_group VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SPICINESS / TASTE / DIET / OTHER',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_code (code),
  UNIQUE KEY uk_tag_name (name),
  CONSTRAINT ck_tag_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_tag_group CHECK (tag_group IN ('SPICINESS', 'TASTE', 'DIET', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品语义标签字典';

CREATE TABLE menu_item_tag (
  item_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (item_id, tag_id),
  KEY idx_item_tag_reverse (tag_id, item_id),
  CONSTRAINT fk_item_tag_menu FOREIGN KEY (item_id) REFERENCES menu_item (id),
  CONSTRAINT fk_item_tag_tag FOREIGN KEY (tag_id) REFERENCES menu_tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品与标签关系';

-- 8. 套餐只组合单品，不允许套餐嵌套；辅助 kind 列由数据库 CHECK 固定。
CREATE TABLE setmeal_item (
  setmeal_id BIGINT NOT NULL,
  setmeal_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'setmeal',
  dish_id BIGINT NOT NULL,
  dish_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'dish',
  quantity INT NOT NULL COMMENT '每份套餐消耗该菜品的份数',
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (setmeal_id, dish_id),
  KEY idx_setmeal_parent (setmeal_id, setmeal_kind),
  KEY idx_setmeal_dish (dish_id, dish_kind, setmeal_id),
  CONSTRAINT fk_setmeal_parent FOREIGN KEY (setmeal_id, setmeal_kind) REFERENCES menu_item (id, kind),
  CONSTRAINT fk_setmeal_dish FOREIGN KEY (dish_id, dish_kind) REFERENCES menu_item (id, kind),
  CONSTRAINT ck_setmeal_kinds CHECK (setmeal_kind = 'setmeal' AND dish_kind = 'dish'),
  CONSTRAINT ck_setmeal_values CHECK (quantity BETWEEN 1 AND 99 AND sort >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐菜品组成';

-- 9. 一个用户最多一个默认地址；可以没有默认地址。
CREATE TABLE address_book (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  consignee VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  building VARCHAR(100) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  default_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN user_id ELSE NULL END) STORED COMMENT '仅用于默认地址唯一约束',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_address_default (default_user_id),
  KEY idx_address_user (user_id, id),
  CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT ck_address_default CHECK (is_default IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工收餐地址';

-- 10-11. 购物车头记录供全量替换和提交订单时锁定，避免并发半覆盖。
CREATE TABLE shopping_cart (
  user_id BIGINT NOT NULL COMMENT '一名员工一份购物车',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '全量替换成功后递增',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT ck_cart_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工购物车头';

CREATE TABLE shopping_cart_item (
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, item_id),
  KEY idx_cart_item_menu (item_id),
  CONSTRAINT fk_cart_item_cart FOREIGN KEY (user_id) REFERENCES shopping_cart (user_id),
  CONSTRAINT fk_cart_item_menu FOREIGN KEY (item_id) REFERENCES menu_item (id),
  CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车商品；不保存成交价格';

-- 12. 订单状态与支付状态分离。地址、姓名快照不依赖后续用户/地址编辑。
CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务订单号，由服务端生成',
  user_id BIGINT NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交幂等键；必须按用户和请求校验',
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下单规范化请求及购物车版本的 SHA-256',
  request_parameters JSON NOT NULL COMMENT '服务端规范化的 fulfillment/addressId/remark，供幂等重试核验',
  cart_version BIGINT NOT NULL COMMENT '下单时购物车版本快照，不跟随清空后的版本变化',
  user_name VARCHAR(64) NOT NULL COMMENT '下单时员工姓名快照',
  status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNPAID',
  pay_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNPAID',
  fulfillment VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  amount DECIMAL(12,2) NOT NULL COMMENT '成交总额；第一版为明细合计，无配送费与优惠',
  recipient VARCHAR(64) NOT NULL COMMENT '收餐人快照',
  phone VARCHAR(32) NOT NULL COMMENT '收餐电话快照',
  address VARCHAR(400) NOT NULL COMMENT '配送地址或自提点快照，不关联 address_book 外键',
  remark VARCHAR(500) NOT NULL DEFAULT '',
  payment_deadline DATETIME(3) NOT NULL,
  paid_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  cancel_reason VARCHAR(255) NULL,
  version BIGINT NOT NULL DEFAULT 0 COMMENT '条件状态更新/Agent 确认检查版本',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_number (number),
  UNIQUE KEY uk_order_request (user_id, request_key),
  KEY idx_order_user_time (user_id, created_at, id),
  KEY idx_order_user_status_time (user_id, status, created_at, id),
  KEY idx_order_status_deadline (status, payment_deadline, id),
  KEY idx_order_status_time (status, created_at, id),
  KEY idx_order_created (created_at, id),
  KEY idx_order_paid (paid_at, id),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT ck_order_status CHECK (status IN ('UNPAID', 'PREPARING', 'READY_DELIVERY', 'DELIVERING', 'READY_PICKUP', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_order_pay_status CHECK (pay_status IN ('UNPAID', 'PAID', 'REFUNDED')),
  CONSTRAINT ck_order_fulfillment CHECK (fulfillment IN ('DELIVERY', 'PICKUP')),
  CONSTRAINT ck_order_route CHECK ((status NOT IN ('READY_DELIVERY', 'DELIVERING') OR fulfillment = 'DELIVERY') AND (status <> 'READY_PICKUP' OR fulfillment = 'PICKUP')),
  CONSTRAINT ck_order_values CHECK (amount > 0 AND version >= 0 AND cart_version >= 0),
  CONSTRAINT ck_order_deadline CHECK (payment_deadline > created_at),
  CONSTRAINT ck_order_payment_time CHECK ((pay_status = 'UNPAID' AND paid_at IS NULL) OR (pay_status IN ('PAID', 'REFUNDED') AND paid_at IS NOT NULL)),
  CONSTRAINT ck_order_paid_state CHECK (status NOT IN ('PREPARING', 'READY_DELIVERY', 'DELIVERING', 'READY_PICKUP', 'COMPLETED') OR pay_status = 'PAID'),
  CONSTRAINT ck_order_unpaid_state CHECK (status <> 'UNPAID' OR pay_status = 'UNPAID'),
  CONSTRAINT ck_order_refunded_state CHECK (pay_status <> 'REFUNDED' OR status = 'CANCELLED'),
  CONSTRAINT ck_order_cancel_time CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL)),
  CONSTRAINT ck_order_complete_time CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单与成交快照';

-- 13. 销售明细按售出商品统计：套餐算套餐，不能再重复累加其组成菜品金额。
CREATE TABLE order_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL COMMENT '原商品 ID，商品逻辑删除后仍保留',
  kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(100) NOT NULL COMMENT '成交商品名称快照',
  price DECIMAL(12,2) NOT NULL COMMENT '成交单价快照',
  quantity INT NOT NULL,
  line_amount DECIMAL(12,2) GENERATED ALWAYS AS (price * quantity) STORED COMMENT '明细小计',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_detail_order_item (order_id, item_id),
  KEY idx_detail_menu (item_id, kind),
  CONSTRAINT fk_detail_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT fk_detail_menu FOREIGN KEY (item_id, kind) REFERENCES menu_item (id, kind),
  CONSTRAINT ck_detail_kind CHECK (kind IN ('dish', 'setmeal')),
  CONSTRAINT ck_detail_values CHECK (price > 0 AND quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品成交明细';

-- 14. 套餐组成下单快照；修改当前套餐不改变旧订单消耗关系。
CREATE TABLE order_detail_component (
  order_detail_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'dish',
  dish_name VARCHAR(100) NOT NULL COMMENT '组成菜品名称快照',
  quantity_per_setmeal INT NOT NULL COMMENT '每份套餐中该菜品的数量快照',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (order_detail_id, dish_id),
  KEY idx_component_dish (dish_id, dish_kind),
  CONSTRAINT fk_component_detail FOREIGN KEY (order_detail_id) REFERENCES order_detail (id),
  CONSTRAINT fk_component_dish FOREIGN KEY (dish_id, dish_kind) REFERENCES menu_item (id, kind),
  CONSTRAINT ck_component_kind CHECK (dish_kind = 'dish'),
  CONSTRAINT ck_component_quantity CHECK (quantity_per_setmeal BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单套餐组成快照';

-- 15. 按订单、库存商品汇总预占，单品和多个套餐共享菜品时合并数量。
CREATE TABLE inventory_reservation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL COMMENT '被扣减的库存商品，包括套餐和组成菜品',
  quantity INT NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HELD' COMMENT 'HELD / CONSUMED / RELEASED / RETURNED',
  expires_at DATETIME(3) NOT NULL COMMENT '与订单支付截止时间一致，由 Service 保证',
  consumed_at DATETIME(3) NULL,
  released_at DATETIME(3) NULL COMMENT '未支付释放或经过审核的回补时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_order_item (order_id, item_id),
  KEY idx_reservation_item_status (item_id, status, id),
  CONSTRAINT fk_reservation_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT fk_reservation_menu FOREIGN KEY (item_id) REFERENCES menu_item (id),
  CONSTRAINT ck_reservation_status CHECK (status IN ('HELD', 'CONSUMED', 'RELEASED', 'RETURNED')),
  CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
  CONSTRAINT ck_reservation_times CHECK (
    (status = 'HELD' AND consumed_at IS NULL AND released_at IS NULL) OR
    (status = 'CONSUMED' AND consumed_at IS NOT NULL AND released_at IS NULL) OR
    (status = 'RELEASED' AND consumed_at IS NULL AND released_at IS NOT NULL) OR
    (status = 'RETURNED' AND consumed_at IS NOT NULL AND released_at IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单库存预占及释放凭据';

-- 16. stock 实际数值变化的流水；支付时 HELD->CONSUMED 不再扣库存。
CREATE TABLE inventory_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  operation_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一业务库存动作的幂等标识',
  operation_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RESERVE / RELEASE / RESTOCK / ADJUST',
  delta INT NOT NULL COMMENT '负数扣减，正数回补/补货',
  stock_after INT NOT NULL,
  actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'USER / ADMIN / SYSTEM',
  actor_id BIGINT NULL COMMENT '按 actor_type 解释；SYSTEM 为空',
  reason VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_operation (item_id, operation_key),
  KEY idx_inventory_item_time (item_id, created_at, id),
  KEY idx_inventory_order (order_id, id),
  CONSTRAINT fk_inventory_menu FOREIGN KEY (item_id) REFERENCES menu_item (id),
  CONSTRAINT fk_inventory_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT ck_inventory_type CHECK (operation_type IN ('RESERVE', 'RELEASE', 'RESTOCK', 'ADJUST')),
  CONSTRAINT ck_inventory_delta CHECK (delta <> 0 AND stock_after >= 0),
  CONSTRAINT ck_inventory_direction CHECK ((operation_type <> 'RESERVE' OR delta < 0) AND (operation_type NOT IN ('RELEASE', 'RESTOCK') OR delta > 0)),
  CONSTRAINT ck_inventory_order_required CHECK (operation_type NOT IN ('RESERVE', 'RELEASE') OR order_id IS NOT NULL),
  CONSTRAINT ck_inventory_actor CHECK ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type IN ('USER', 'ADMIN') AND actor_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可售库存增减流水';

-- 17. 订单事件与状态更新在同一事务内写入，版本作为事件去重依据。
CREATE TABLE order_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  order_version BIGINT NOT NULL COMMENT '此次履约状态变更后的订单版本',
  from_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建订单时为空',
  to_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_id BIGINT NULL,
  note VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_history_order_version (order_id, order_version),
  CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT ck_history_from CHECK (from_status IS NULL OR from_status IN ('UNPAID', 'PREPARING', 'READY_DELIVERY', 'DELIVERING', 'READY_PICKUP', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_history_to CHECK (to_status IN ('UNPAID', 'PREPARING', 'READY_DELIVERY', 'DELIVERING', 'READY_PICKUP', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_history_version CHECK (order_version >= 0),
  CONSTRAINT ck_history_actor CHECK ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type IN ('USER', 'ADMIN') AND actor_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单履约状态历史';

-- 18. 支付尝试可多次，一张订单最多一笔成功支付；只预留真实渠道字段。
CREATE TABLE payment_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id BIGINT NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  channel VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK / WECHAT / ALIPAY；不代表渠道已实现',
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SUCCEEDED / FAILED / CLOSED',
  provider_transaction_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  success_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'SUCCEEDED' THEN order_id ELSE NULL END) STORED,
  paid_at DATETIME(3) NULL,
  error_code VARCHAR(64) NULL COMMENT '脱敏错误码，不存供应商原始报文',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_payment_request (order_id, request_key),
  UNIQUE KEY uk_payment_success_order (success_order_id),
  UNIQUE KEY uk_payment_provider (channel, provider_transaction_id),
  UNIQUE KEY uk_payment_id_order (id, order_id),
  KEY idx_payment_status_time (status, created_at, id),
  KEY idx_payment_paid (status, paid_at, id),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT ck_payment_channel CHECK (channel IN ('MOCK', 'WECHAT', 'ALIPAY')),
  CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CLOSED')),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_time CHECK ((status = 'SUCCEEDED' AND paid_at IS NOT NULL) OR (status <> 'SUCCEEDED' AND paid_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水；学习阶段使用 MOCK';

-- 19. 第一版只支持整单全额退款，一张订单一笔逻辑退款，失败重试同一记录。
CREATE TABLE refund_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  refund_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id BIGINT NOT NULL,
  payment_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / SUCCEEDED / FAILED',
  provider_refund_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  reason VARCHAR(255) NOT NULL,
  requested_by BIGINT NOT NULL COMMENT '发起退款的运营账户',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  refunded_at DATETIME(3) NULL,
  error_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  UNIQUE KEY uk_refund_order (order_id),
  KEY idx_refund_payment_order (payment_id, order_id),
  KEY idx_refund_retry (status, next_retry_at, id),
  KEY idx_refund_time (status, refunded_at, id),
  CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id, order_id) REFERENCES payment_record (id, order_id),
  CONSTRAINT fk_refund_employee FOREIGN KEY (requested_by) REFERENCES employee (id),
  CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT ck_refund_values CHECK (amount > 0 AND retry_count >= 0),
  CONSTRAINT ck_refund_time CHECK ((status = 'SUCCEEDED' AND refunded_at IS NOT NULL) OR (status <> 'SUCCEEDED' AND refunded_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='整单全额退款流水';

-- 20. Agent 会话绑定一种身份，不允许通过请求 userId/role 任意切换。
CREATE TABLE agent_session (
  id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端生成的不可预测公开会话 ID',
  owner_role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user / admin',
  owner_user_id BIGINT NULL,
  owner_employee_id BIGINT NULL,
  title VARCHAR(100) NOT NULL DEFAULT '',
  context_summary TEXT NULL COMMENT '脱敏、限长的会话摘要，不是思维链或业务事实源',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / CLOSED',
  expires_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0 COMMENT '串行化会话请求的版本',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session_user (owner_user_id, updated_at, id),
  KEY idx_session_employee (owner_employee_id, updated_at, id),
  KEY idx_session_expiry (status, expires_at, id),
  CONSTRAINT fk_session_user FOREIGN KEY (owner_user_id) REFERENCES user_account (id),
  CONSTRAINT fk_session_employee FOREIGN KEY (owner_employee_id) REFERENCES employee (id),
  CONSTRAINT ck_session_owner CHECK (
    (owner_role = 'user' AND owner_user_id IS NOT NULL AND owner_employee_id IS NULL) OR
    (owner_role = 'admin' AND owner_employee_id IS NOT NULL AND owner_user_id IS NULL)
  ),
  CONSTRAINT ck_session_status CHECK (status IN ('ACTIVE', 'CLOSED')),
  CONSTRAINT ck_session_values CHECK (version >= 0 AND expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话及身份归属';

-- 21. 只保存可见消息/裁剪后工具观测，不保存模型内部推理过程。
CREATE TABLE agent_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sequence_no INT NOT NULL COMMENT '会话内递增顺序，由锁定会话后分配',
  role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user / assistant / tool',
  content TEXT NOT NULL COMMENT '脱敏且限长的可见文本',
  structured_data JSON NULL COMMENT '裁剪后的展示数据或工具 Observation，不含敏感全文',
  model VARCHAR(100) NULL COMMENT '实际使用的模型名称，非密钥',
  input_tokens INT NULL,
  output_tokens INT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_sequence (session_id, sequence_no),
  UNIQUE KEY uk_message_session_id (session_id, id),
  CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
  CONSTRAINT ck_message_role CHECK (role IN ('user', 'assistant', 'tool')),
  CONSTRAINT ck_message_values CHECK (sequence_no > 0 AND (input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 可见消息及工具观测';

-- 22. 工具注册定义在 Java 白名单；数据库只保存安全执行记录。
CREATE TABLE agent_tool_call (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  message_id BIGINT NOT NULL COMMENT '产生本次调用的 assistant 消息',
  call_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该消息内的工具调用标识',
  tool_name VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tool_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READ / WRITE',
  status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED / SUCCEEDED / FAILED / AWAITING_CONFIRMATION',
  arguments_summary JSON NULL COMMENT '脱敏参数摘要，不是直接执行任意 SQL 的参数',
  result_summary JSON NULL COMMENT '裁剪后的结构化结果',
  summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT '面向 traces 的安全摘要',
  error_code VARCHAR(64) NULL,
  duration_ms INT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_message_call (message_id, call_key),
  UNIQUE KEY uk_tool_session_id (session_id, id),
  KEY idx_tool_session_time (session_id, started_at, id),
  KEY idx_tool_message (session_id, message_id),
  CONSTRAINT fk_tool_message FOREIGN KEY (session_id, message_id) REFERENCES agent_message (session_id, id),
  CONSTRAINT ck_tool_type CHECK (tool_type IN ('READ', 'WRITE')),
  CONSTRAINT ck_tool_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED', 'AWAITING_CONFIRMATION')),
  CONSTRAINT ck_tool_confirm_type CHECK (status <> 'AWAITING_CONFIRMATION' OR tool_type = 'WRITE'),
  CONSTRAINT ck_tool_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具调用安全审计';

-- 23. 写工具确认记录；第一版仅承载同库内可事务化的订单取消/商品上下架。
CREATE TABLE agent_action (
  id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可预测的公开确认记录 ID',
  session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_tool_call_id BIGINT NOT NULL,
  tool_name VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  target_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ORDER / MENU_ITEM',
  target_id BIGINT NOT NULL COMMENT '多态业务对象 ID，由白名单 Service 校验',
  expected_version BIGINT NOT NULL COMMENT '生成确认卡片时的目标版本',
  parameters JSON NOT NULL COMMENT '服务端校验及规范化后的写操作参数',
  parameters_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化参数 SHA-256',
  label VARCHAR(150) NOT NULL,
  detail VARCHAR(1000) NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / EXECUTING / SUCCEEDED / FAILED / CANCELLED / EXPIRED',
  expires_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  result_data JSON NULL COMMENT '重复确认返回的既有安全结果',
  error_code VARCHAR(64) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_action_tool_call (source_tool_call_id),
  KEY idx_action_session (session_id, created_at, id),
  KEY idx_action_source (session_id, source_tool_call_id),
  KEY idx_action_expiry (status, expires_at, id),
  CONSTRAINT fk_action_tool FOREIGN KEY (session_id, source_tool_call_id) REFERENCES agent_tool_call (session_id, id),
  CONSTRAINT ck_action_target CHECK (target_type IN ('ORDER', 'MENU_ITEM')),
  CONSTRAINT ck_action_status CHECK (status IN ('PENDING', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
  CONSTRAINT ck_action_values CHECK (target_id > 0 AND expected_version >= 0 AND version >= 0 AND expires_at > created_at),
  CONSTRAINT ck_action_success CHECK (status <> 'SUCCEEDED' OR (confirmed_at IS NOT NULL AND finished_at IS NOT NULL AND result_data IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 待确认写操作及幂等结果';

-- 24. 与订单同事务写事件，提交后再推送；多实例/重试由应用领取逻辑实现。
CREATE TABLE notification_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '如 order:{id}:version:{n}:NEW_ORDER',
  order_id BIGINT NOT NULL,
  order_version BIGINT NOT NULL,
  event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'NEW_ORDER / ORDER_STATUS_CHANGED',
  payload JSON NOT NULL COMMENT '最小推送载荷，只有 type/orderNumber 等，不含地址电话',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  locked_until DATETIME(3) NULL COMMENT '领取租约到期时间，应用负责回收',
  last_error VARCHAR(255) NULL COMMENT '脱敏错误摘要',
  sent_at DATETIME(3) NULL COMMENT '表示推送尝试成功，不代表浏览器已接收',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event (event_key),
  KEY idx_outbox_dispatch (status, next_retry_at, id),
  KEY idx_outbox_lease (status, locked_until, id),
  KEY idx_outbox_order (order_id, order_version),
  CONSTRAINT fk_outbox_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT ck_outbox_type CHECK (event_type IN ('NEW_ORDER', 'ORDER_STATUS_CHANGED')),
  CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
  CONSTRAINT ck_outbox_values CHECK (retry_count >= 0 AND order_version >= 0),
  CONSTRAINT ck_outbox_sent CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单通知事务发件箱；可后期实现';
