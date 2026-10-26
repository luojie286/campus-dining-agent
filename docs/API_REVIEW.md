# 2026-10-16 接口设计复核与范围覆盖

## 本版交付

- [API_OPENAPI.json](API_OPENAPI.json)：重新导入Apifox的单一文件，OpenAPI3.0.3，98个操作、86个路径、119个模型。
- [API_CONTRACT.md](API_CONTRACT.md)：身份/权限、ID、金额/时间、分页、事务、状态机、支付退款、统计口径及Agent规则。
- [API_MODULES.md](API_MODULES.md)：98项完整路径/方法/权限/实现状态/客户端适配目录。
- [APIFOX_IMPORT.md](APIFOX_IMPORT.md)：覆盖导入与环境、鉴权、用例检查。
- [API_IMPLEMENTATION_GAPS.md](API_IMPLEMENTATION_GAPS.md)：当前前端、数据库及后端尚未具备的能力，防止误以为文档更新等于实现。

## 用户所列功能逐项对应

| 要求 | 本版落点 |
| --- | --- |
| 新增运营成员 | POST /api/admin/employee，必填初始密码，角色固定OPERATOR，manager权限 |
| 成员分页查询 | GET /api/admin/employee/page；旧/list保持数组 |
| 启用禁用成员 | PUT /api/admin/employee/{id}/status，仅普通运营目标 |
| 编辑成员 | PUT /api/admin/employee，禁止改角色、密码、登录名 |
| 分类管理 | category创建/更新/删除/list/page/status，关联与类型校验 |
| 菜品新增修改删除启停 | dish创建/更新/单删/批删/status/详情 |
| 公共字段自动填充 | 服务端内部审计机制，客户端不传操作人/时间，无任意填充接口 |
| 菜品分页 | admin/user的dish/page，统一外部ID与分页顺序 |
| 套餐新增修改删除启停 | setmeal同类接口，组成、共享库存、关联缓存校验 |
| 套餐分页 | admin/user的setmeal/page及menu/page |
| 店铺营业设置 | admin/user查询shop，运营PUT shop |
| 微信小程序开发 | 共用user接口，登录/目录/地址/购物车/订单/支付/WS链路 |
| 微信登录 | user/wechat/login；独立Web绑定流程，不按手机号自动合并 |
| 商品浏览 | user category/list、dish/setmeal/menu分页及详情 |
| 菜品缓存 | 内部Redis回填、TTL、分类/库存/状态/组成关联失效，交易查数据库 |
| 套餐缓存 | 同上，套餐组成变更与组成菜品变更联动 |
| 添加查看清空购物车 | shoppingCart/list、add、数量更新、移除、clean；兼容全量PUT |
| 地址簿 | addressBook列表/详情/新增/修改/删除/设默认，归属和快照 |
| 用户下单 | order/submit，稳定键、防超卖、整车共享库存、事务与快照 |
| 订单支付 | MOCK旧/pay与WECHAT预支付/查单分开，通知验签、退款与补偿 |
| 订单状态定时处理 | 内部超时扫描与外部关单协调；不提供任意调度执行HTTP接口 |
| 来单提醒 | 付款成功后NEW_ORDER，仅运营，事务后事件 |
| 客户催单 | 本人order/{id}/urge，60秒冷却、5次总上限、ORDER_URGED |
| 营业额统计 | report/revenue，支付总额/实际退款/现金净额分开 |
| 用户统计 | report/users，新增/累计/下单/支付用户与区间去重 |
| 订单统计 | report/orders，创建/历史支付/有效/完成/取消及明确时间口径 |
| 销量排行 | report/sales，商品成交快照，套餐不重复拆分营收 |
| 工作台 | admin/workbench，今天指标+全部日期未完结待办 |
| Excel导出 | report/export，真实xlsx，四种报表，范围/行数限制、无敏感原始资料 |
| 智能Agent | 真实工具循环、权限投影、硬筛候选、同口径分析、持久确认/幂等及本人会话 |

## 重要设计纠正

1. 账号管理员不是可以任意授予管理员权限；本版只管理普通运营，无公开创建/升级管理员入口。
2. 响应对象不复用成可写请求：初始密码只写不回，角色/审计字段不由请求决定。
3. /list和/page分开，不为分页修改旧数组形状。商品/分类/员工状态均有明确专用接口，同时标注旧PUT兼容范围。
4. 普通商品PUT不覆盖库存；库存条件写入和实际下单事务分开设计。
5. Web账户和微信身份共用业务层，但不因手机号相同自动跨账户合并。
6. 未付款创建订单与付款后的备餐来单分开；客户催单不改变履约状态。
7. 模拟付款、微信预支付、支付事实、退款中、退款完成分开；前端回调不直接更新付款状态。
8. 超时不能只改本地订单再释放库存；微信查单/关单与晚到付款补偿必须协调。
9. 历史支付总额、退款现金和净现金分开；工作台待办不能只统计近7天。
10. Excel二进制、微信通知ACK、普通JSON Result分开；Agent内部工具与普通外部API分开。

## 本次验证记录

- JSON可解析且UTF8无替换字符。
- 98个operationId无重复，路径参数定义完整，所有本地$ref可解析。
- 校验925处模型/请求/响应示例，覆盖required、类型、enum、正则、长度、数值范围/分精度、数组约束、时区和oneOf分支。
- 原52个方法/路径组合及operationId全部保留。
- 检查角色敏感创建字段不可写、manager接口权限、稳定幂等键、微信通知204和签名头、xlsx MIME以及文档链接。
- 对照开始时哈希，99个现有backend、frontend/src及infra文件内容未变；本次只改docs及被忽略的本地验证临时文件。
- 仅静态合同复核，没有执行SQL、真实接口/并发测试、小程序/微信支付、LLM联调或实际Apifox导入；未使用第三方完整OpenAPI规范验证器。实现仍按模块完成后单独验收。

完整路径的接口标记“已实现”只表示5个Controller入口源码已存在。其余待实现设计不能写成已完成项目能力。
