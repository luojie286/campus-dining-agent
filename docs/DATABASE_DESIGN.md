# 智能园区餐饮平台数据库设计（当前学习版）

日期：2026-10-10。数据库：`campus_dining`。技术环境仍为 Java 21 / Spring Boot 3.4.x / MyBatis-Plus 3.5.x / MySQL 8 / Redis 7。

> 2026-10-16 接口基线说明：本文和 infra/schema.sql 保留13表学习底座，不代表已经覆盖微信身份、真实支付/退款、幂等、通知接收及新账号角色。完整外部合同以 [API_CONTRACT.md](API_CONTRACT.md) 为准，需新增/迁移的持久化能力见 [API_IMPLEMENTATION_GAPS.md](API_IMPLEMENTATION_GAPS.md)。本次不改SQL、不执行迁移，也不将temp的另一套表结构混入当前库。

本版按你提供的苍穹外卖 `sky.sql` 和《数据库设计文档.md》的难度设计：**沿用课程 11 张表，再加营业设置、Agent 写操作确认，共 13 张表。** 采用自增主键、普通字段、少量索引和逻辑关联，不使用上一版的物理外键、CHECK、生成列、独立预占表、支付退款流水、工具审计和事务发件箱。

- **现在使用**：[infra/schema.sql](../infra/schema.sql) 和本文。
- **最终架构保留版，暂不使用**：[temp/DATABASE_DESIGN.md](../temp/DATABASE_DESIGN.md)、[temp/schema.sql](../temp/schema.sql)，旧文件原样保留。
- 旧文档是完整存档，其原有相对链接仍按原目录编写；阅读当前接口请打开 [API_CONTRACT.md](API_CONTRACT.md)。

这两套脚本都指向 campus_dining，表结构不同，不能先后直接叠加执行。最终阶段切换到 temp 中的架构时，需要另写迁移和数据转换语句。当前只是提供设计和 SQL，没有执行建表或编写后端业务。

## 1. 表清单

| 序号 | 表名 | 中文名称 | 本阶段作用 |
| --- | --- | --- | --- |
| 1 | employee | 食堂运营账号 | 管理端登录与人员管理 |
| 2 | category | 分类 | 菜品分类、套餐分类 |
| 3 | dish | 菜品 | 菜品 CRUD、价格、可售量 |
| 4 | dish_flavor | 菜品口味选项 | 保留课程结构，学口味模块时再使用 |
| 5 | setmeal | 套餐 | 套餐 CRUD、价格、独立可售量 |
| 6 | setmeal_dish | 套餐菜品关系 | 套餐由哪些菜品组成、各多少份 |
| 7 | user | 园区员工点餐账号 | 员工端用户名密码登录 |
| 8 | address_book | 地址 | 园区楼栋与收餐位置 |
| 9 | shopping_cart | 购物车 | 一行记录一个商品 |
| 10 | orders | 订单 | 状态、支付标记、收餐快照 |
| 11 | order_detail | 订单明细 | 成交商品、数量、单价快照 |
| 12 | shop | 营业设置 | 当前前端的营业开关和自提点 |
| 13 | agent_action | Agent 操作确认 | 学到 AI 写工具时再使用 |

employee 是食堂运营人员，user 是园区点餐员工，不能因为中文都叫“员工”而混用。两张账号表的 ID 可以相同，登录身份必须同时包含角色和 ID。

## 2. 基本约定

- 所有表使用 InnoDB、utf8mb4；除 shop 固定 ID=1 外，主键使用 BIGINT 自增。实体使用 Java Long，MyBatis-Plus 自增主键选择 `IdType.AUTO`。
- `category_id`、`user_id`、`dish_id` 等是**逻辑关联字段**，与课程相同：数据库不自动检查关联存在或执行级联删除，Service 负责校验。
- 金额用 DECIMAL(10,2)，单位元，Java 用 BigDecimal。数量、价格、库存、枚举合法性均由 Service 校验。
- 审计字段沿用课程命名：create_time、update_time、create_user、update_user。时间按 Asia/Shanghai 处理，接口转换为带 +08:00 偏移的 ISO 字符串。
- password 字段保留课程名字，但长度为 255，存密码哈希；不复制课程脚本的明文示例密码，也不插入默认登录账号。
- 文本字段中的 JSON 只是普通字符串，由 Java 序列化/解析并限制长度；无需学习数据库 JSON 函数或专用 TypeHandler。

## 3. 表结构

### 3.1 employee：运营账号

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| name | varchar(64) | 姓名 |
| username | varchar(64) | 登录名，唯一 |
| password | varchar(255) | 密码哈希，不在接口返回 |
| phone | varchar(32) | 电话 |
| status | int | 1 启用，0 停用 |
| create_time / update_time | datetime | 创建、修改时间 |
| create_user / update_user | bigint | 创建、修改的运营 ID，可空 |

与课程相比不要求收集身份证号、性别，当前管理页面也不需要这些字段。

### 3.2 category：分类

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| type | int | 1 菜品分类，2 套餐分类 |
| name | varchar(64) | 分类名称，唯一 |
| sort | int | 非负排序值 |
| status | int | 1 启用，0 禁用 |
| create_time / update_time | datetime | 审计时间 |
| create_user / update_user | bigint | 运营审计 ID |

有关联商品的分类不能删除；菜品必须选 type=1 的分类，套餐选 type=2。

### 3.3 dish：菜品

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 数据库菜品 ID，自增 |
| name | varchar(64) | 菜品名，唯一 |
| category_id | bigint | 菜品分类 ID |
| price | decimal(10,2) | 当前销售单价 |
| image | varchar(255) | 图片地址，可空，留给课程图片功能 |
| description | varchar(500) | 描述 |
| status | int | 1 起售，0 停售；新增默认停售 |
| stock | int | 剩余可售份数，已扣除待支付预占 |
| tags | varchar(255) | 固定标签 JSON 数组字符串，默认 `[]` |
| art | varchar(16) | 前端插画键，默认 chicken |
| create_time / update_time | datetime | 审计时间 |
| create_user / update_user | bigint | 运营审计 ID |

相较课程只补 stock、tags、art，直接对应当前前端，不额外建库存或标签字典表。tags 示例：`["不辣","清淡"]`；后端解析为 `List<String>` 后返回前端数组。

art 可选 chicken/fish/beef/salad/noodle/tofu，只用于展示。tags 必须是维护过的事实，不能同时写互斥辣度，也不能让模型凭空写入。

### 3.4 dish_flavor：可选口味

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| dish_id | bigint | 数据库菜品 ID |
| name | varchar(32) | 选项类别，如辣度、忌口 |
| value | varchar(255) | 可选值数组字符串，如 `["不辣","微辣"]` |

这是课程的“顾客可选择哪些口味”，不是 tags 的“商品固定属性”。value 中包含“不辣”只代表允许选择，不证明默认商品不辣。**当前前端没有选口味页面，可以暂时不写这个模块，不把 flavor 选项直接转成 Agent 推荐标签。**

### 3.5 setmeal：套餐

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 数据库套餐 ID，自增 |
| category_id | bigint | 套餐分类 ID |
| name | varchar(64) | 套餐名，唯一 |
| price | decimal(10,2) | 套餐整体售价，不等于关联菜品价之和 |
| status | int | 1 起售，0 停售；新增默认停售 |
| description | varchar(500) | 套餐说明 |
| image | varchar(255) | 图片地址，可空 |
| stock | int | 套餐自身剩余可售量 |
| tags / art | varchar(255) / varchar(16) | 与菜品相同 |
| create_time / update_time | datetime | 审计时间 |
| create_user / update_user | bigint | 运营审计 ID |

套餐可买量还受组成菜品库存限制；没有组成、分类禁用、组成菜品停售或被删除时不允许售卖。

### 3.6 setmeal_dish：套餐组成

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| setmeal_id | bigint | 数据库套餐 ID |
| dish_id | bigint | 数据库菜品 ID |
| name | varchar(64) | 关联时菜品名称，课程冗余字段 |
| price | decimal(10,2) | 关联时菜品单价，课程冗余字段 |
| copies | int | 每份套餐包含几份该菜品，正整数 |

同一套餐同一菜品只能一条。查询组合返回 composition；套餐不能嵌套套餐。这里冗余的 name/price 不用于计算订单成交价或覆写历史订单。

### 3.7 user：园区员工点餐账号

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| username | varchar(64) | 登录名，唯一 |
| password | varchar(255) | 密码哈希 |
| name | varchar(64) | 员工姓名 |
| phone | varchar(32) | 电话 |
| status | int | 1 启用，0 停用 |
| create_time / update_time | datetime | 创建、修改时间 |

课程用微信 openid 登录，这个项目当前是 Web 用户名密码登录，因此直接改为 username/password，不引入微信字段。SQL 使用反引号包裹 `user` 表名，Mapper 编写时也保持正确引用。

### 3.8 address_book：园区地址

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| user_id | bigint | 所属点餐员工 ID |
| consignee | varchar(64) | 收餐人 |
| phone | varchar(32) | 电话 |
| building | varchar(100) | 园区楼栋，如 A 座 |
| detail | varchar(255) | 楼层、房间等位置 |
| is_default | tinyint | 1 默认，0 非默认 |

去掉当前园区页面不用的省、市、区字段，保留前端真正提交的 building/detail。所有地址操作使用当前登录 user_id；不能信任请求中的 userId。

默认地址不使用生成列：在事务中锁定该 user 行，先清除旧默认，再设置新默认，Service 保证每人最多一条默认地址。

### 3.9 shopping_cart：购物车

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| user_id | bigint | 员工归属 |
| dish_id / setmeal_id | bigint，可空 | 只能且必须填写一个，用数据库 ID |
| name / image | varchar(64/255) | 展示数据，不作为商品真实性依据 |
| dish_flavor | varchar(255)，可空 | 预留课程口味选择，当前不使用 |
| number | int | 商品数量，前端字段 quantity |
| amount | decimal(10,2) | 展示单价，提交订单时重新查真实价格 |
| create_time | datetime | 创建时间 |

一行一个商品，不再分购物车头表和条目表。当前前端是全量替换：在事务里锁定当前 user 行，删除该用户原购物车、插入校验后的新条目。提交订单也先锁同一 user 行，防止替换与下单交叉执行；仅锁现有购物车行不能处理空购物车并发。

同用户同菜品/套餐各有唯一索引；当前不支持同一商品按多种口味拆成多行。这里的 NULL 唯一索引不会代替“两个商品 ID 只能填一个”的 Service 校验。

### 3.10 orders：订单

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| number | varchar(50) | 唯一业务订单号，不能只靠当前毫秒生成 |
| status | int | 履约状态，见第 5 节 |
| user_id | bigint | 下单员工 ID |
| address_book_id | bigint，可空 | 配送时本人地址 ID，自提可空 |
| fulfillment | varchar(16) | DELIVERY 配送 / PICKUP 自提 |
| order_time | datetime | 下单时间，映射 createdAt |
| payment_deadline | datetime | 支付截止时间，例如创建后 15 分钟 |
| checkout_time | datetime，可空 | 成功支付时间，映射 paidAt |
| pay_status | tinyint | 0 未支付，1 已支付，2 已退款 |
| amount | decimal(10,2) | 订单总额，等于明细单价×数量的合计 |
| remark | varchar(500) | 备注 |
| phone / address | varchar(32/400) | 收餐电话和配送地址/自提点快照 |
| user_name / consignee | varchar(64) | 下单员工与收餐人姓名快照 |
| cancel_reason / cancel_time | varchar(255) / datetime，可空 | 取消原因和时间 |
| finish_time | datetime，可空 | 完成时间 |
| inventory_snapshot | text | 服务端扣减库存清单，普通 JSON 字符串 |
| events | text | 订单事件数组，普通 JSON 字符串 |

address_book_id 是关联信息，历史展示读取订单里的 address/phone/consignee 快照。这样后续编辑或删除地址不会改变旧订单。未提供配送费、优惠、餐具、预约配送等当前页面没有的功能。

inventory_snapshot 用一个字段替代复杂预占表，保存下单时真正扣了哪些库存，例如：

```json
[{"kind":"setmeal","id":3,"quantity":2},{"kind":"dish","id":8,"quantity":2}]
```

这里 id 是对应表的**数据库 ID**，包括套餐本身和组成菜品；同一库存商品先汇总再写一项。该字段仅由后端生成、创建后不改，不暴露给前端。取消时读取它回补，不能按最新套餐组成回补。

events 示例：`[{"status":"UNPAID","time":"2026-10-10T12:00:00+08:00","note":"订单创建"}]`。每次状态变更锁定订单，同事务追加一个事件，查询时解析为前端 events 数组；不单独创建订单历史表。

### 3.11 order_detail：成交明细

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 自增主键 |
| order_id | bigint | 所属订单 |
| dish_id / setmeal_id | bigint，可空 | 只能且必须填一个，数据库商品 ID |
| name / image | varchar(64/255) | 成交时商品展示快照 |
| dish_flavor | varchar(255)，可空 | 预留成交口味 |
| number | int | 成交数量 |
| amount | decimal(10,2) | **成交单价**，不是此行总价 |

明细小计由 Java 计算 `amount × number`，不使用生成列。接口 OrderLine.price 对应这里 amount。修改商品价格、名称之后，历史订单仍读取此表，不能拿最新 dish/setmeal 值覆盖。

### 3.12 shop：营业设置

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 单食堂固定 1 |
| name | varchar(64) | 食堂名称 |
| status | int | 1 营业，0 打烊；映射 open:boolean |
| opening_hours | varchar(255) | 展示营业时间，映射 openingHours |
| pickup_point | varchar(255) | 自提地点，映射 pickupPoint |
| update_time | datetime | 修改时间 |

不设计多门店。首次初始化自行插入 id=1，没有记录时提示未配置；营业时间文本不会自动实现排班或定时开关。

### 3.13 agent_action：写工具确认（最后学习）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 内部自增主键 |
| action_id | varchar(32) | 唯一公开确认 ID，使用安全随机源生成，不返回自增 ID |
| actor_id / actor_role | bigint / varchar(16) | 操作人 ID 和 user/admin 角色 |
| session_id | varchar(64) | 绑定身份的服务端会话 ID |
| tool_name | varchar(64) | 已注册白名单写工具 |
| params | text | 服务端验证后的 JSON 参数，含数据库目标 ID 和预期状态 |
| label / detail | varchar(150/1000) | 卡片标题与影响说明 |
| status | int | 0 待确认，1 成功，2 撤销，3 过期，4 失败 |
| result | text，可空 | 成功后的安全 content 文本，供重复确认返回 |
| expire_time / confirm_time | datetime | 到期、确认时间；未确认时 confirm_time 可空 |
| create_time | datetime | 创建时间 |

前端 action.id 返回 action_id，expiresAt 返回 expire_time。生成确认卡片时只写 status=0，不执行业务。confirm 校验身份、会话、有效期，锁定此记录、复核目标存在及状态，在同一个事务中执行业务并保存 status=1/result；重复确认返回既有结果。参数生成后不可修改，确认请求不能重新指定目标。

cancel 只允许待确认变为撤销，不能撤销已经执行业务。过期、越权、失效目标都不能执行。这个最小方案用于同库的订单取消、套餐上下架，不声称保证外部真实支付执行一次。

**AI 会话和裁剪后的上下文先放 Redis，查询工具的安全 trace 直接随响应返回，不再建会话、消息、工具日志三组表。** 每次访问校验角色和 ID，设置 TTL、轮数、调用次数和上下文上限；学习版会话到期后不保证完整历史恢复。真实 Tool Registry、LLM 调用仍由你自己实现。

## 4. 分表商品如何接现有前端

课程 dish/setmeal 各自自增，可能同时有 id=1。当前前端的购物车只有数字 itemId，不能直接混用两个原始 ID。采用一个简单的 **VO 编号转换**，数据库仍按课程方式存储：

| 商品类型 | 数据库 ID | 对外商品 ID | 反向转换 |
| --- | --- | --- | --- |
| 菜品 dish | d | `d × 2`（偶数） | `itemId / 2` |
| 套餐 setmeal | s | `s × 2 + 1`（奇数） | `(itemId - 1) / 2` |

例如 dish.id=1 对外为 2，setmeal.id=1 对外为 3。Service/VO 统一处理转换，不改现有前端，也不把转换后的 ID 写进数据库关联列。

- 菜单 id、CartLine.itemId、OrderLine.itemId、Agent 返回商品 ID、sales.id 都用对外 ID。
- composition.dishId 也返回偶数菜品对外 ID；保存套餐时转换回原 dish.id。
- 菜品/套餐 PUT 和 DELETE 路径中的商品 ID 也按同一规则解码，同时验证与 kind/路径一致；id=0 只作为新增标记。
- 分类、订单、用户、地址 ID 不转换。
- 所有对外数字 ID 都必须落在 JavaScript 安全整数范围内。保持自增，不直接改用雪花 ID。

这是后续写后端时要实现的映射规则，本次没有代写 DTO、VO 或转换代码。

## 5. 订单状态与简单库存流程

### 5.1 状态对应

| 数据库 status | 当前含义 | 前端字符串 |
| --- | --- | --- |
| 1 | 待支付 | UNPAID |
| 2 | 制作中 | PREPARING |
| 3 | 待配送 | READY_DELIVERY |
| 4 | 配送中 | DELIVERING |
| 5 | 已完成 | COMPLETED |
| 6 | 已取消 | CANCELLED |
| 7 | 待自提 | READY_PICKUP |

这不是原课程“2待接单、3已接单”的含义，也不是原 SQL 注释里的“7退款”。为匹配本项目前端，状态 7 是待自提，退款仍单独用 pay_status=2。

配送：1→2→3→4→5；自提：1→2→7→5。员工可取消本人状态 1；超时任务只取消已到截止时间的状态 1。运营的已支付取消要实现明确的学习模拟退款规则后才开放；未实现则返回不允许，不能只把状态改为 6 假装退款成功。

pay_status 对应 0→UNPAID、1→PAID、2→REFUNDED。已退款订单保留原金额和成功付款时间，不能把历史支付抹掉。

### 5.2 库存只使用 stock 和一个快照字段

1. 提交订单时检查营业、分类、商品状态、数量及本人地址，读取真实价格。
2. 汇总单品、套餐和组成菜品的需求，锁定相关商品行，校验并扣减 stock。
3. 同一个事务写 orders、order_detail、inventory_snapshot、初始 events，清空本人购物车；任意一步失败全部回滚。
4. 支付只修改订单支付/履约状态，**不再次扣库存**。
5. 未支付取消/超时在事务内锁订单，只有 status=1 的请求能改为 6，并按原 inventory_snapshot 回补一次。重复取消不能重复回补。

套餐可买量是“套餐 stock、各组成菜品 stock/份数向下取整”中的最小值，但整车还要合并共享库存。例如单买 A 两份、再买含 A 一份的套餐三份，应扣 A 五份，不能只分别检查各行。

商品锁按固定次序，例如先按类型再按数据库 ID 排序；套餐组成修改和下单也采用兼容的锁定方式。条件扣减带 `stock >= 需要量` 并检查影响行数。支付、超时和取消共用订单行锁，不能先读状态后无条件覆盖。

目录修改与库存扣减需协调，不能把编辑表单携带的旧库存当成数据库当前值直接覆盖。删除先校验停售、套餐引用和未完成订单的明细/库存快照引用；历史明细保留名称、单价，不依赖商品继续存在。

学习版没有库存审计或完整支付流水；订单事务可以保证这套简单流程，不能宣称它已经具备完整生产支付、对账、补偿和审计能力。已支付取消默认不回补餐品，是否仍可售需要运营规则判断。

先实现支付、取消等状态操作的重复请求保护。订单号唯一并不能防止网络重试创建另一张新订单；当前前端未发送稳定提交键，端到端下单幂等需要之后补充调用方稳定键和后端记录，本学习版暂不单独设计幂等表。

## 6. 查询和统计

只建立课程常见的唯一索引和关联/时间索引：账号与分类/商品名称唯一，套餐组成去重，员工地址/购物车、本人订单、订单明细、超时扫描、付款时间、action 查找。

统计直接查询 orders/order_detail，不另建统计表：

- revenue：按 checkout_time 范围统计曾成功付款的订单 amount 合计，退款后仍算历史支付总额；不要称为净收入。
- paidOrders/averageAmount：采用同一付款时间口径；订单总量、完成量、取消率按 order_time 创建日期范围和当前状态计算。
- trend：按付款日统计金额与支付单量，缺日补 0。
- sales：按所选付款日期范围、当前 pay_status=1 的成交明细计算数量和 `amount×number`，排除已模拟退款订单；套餐按套餐统计，不再重复累加组成菜品收入。
- 所有状态计数补全 0，metricNote 说明各字段口径。收入查询不要 JOIN 多条明细后重复 SUM 主表 amount。

真实退款、财务净额和历史统计快照留到最终架构阶段。

## 7. 当前使用和学习顺序

1. 手动打开 `infra/schema.sql`，确认连接目标为 MySQL 8、数据库为 campus_dining，在空库中按顺序运行。新脚本已经包含建空库语句。
2. 先做 employee/user 登录、category、dish，然后套餐及 setmeal_dish。
3. 再做地址、购物车、订单明细和简单库存事务，最后接 Redis 缓存、超时任务、WebSocket。
4. 基础业务能运行后，再学习 Agent：查询/推荐工具使用已有 Service，写工具用 agent_action 确认。

脚本不含删除旧表、不含初始化账号/菜单、不关闭外键检查；已有同名表时会报错，不用于升级旧表。多条建表语句可能部分成功，看到错误先检查原因。**本次只完成文档和建表文件，并做静态检查，未连接数据库或实际执行 SQL。**

temp 旧架构保留原样，当前版本只作为学习阶段使用；前后端源代码、配置、参考资料和 Nginx 均未改动。
