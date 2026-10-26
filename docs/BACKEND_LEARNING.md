# 后端学习与实现路线

本文件给出任务与验收点，不提供业务答案。当前业务 Controller、Service、Mapper、Entity、DTO、VO、Agent 均由你编写。

## 0. 先理解当前环境

打开 `backend/pom.xml`：父工程统一 Java、Spring Boot 与依赖版本。三个子模块沿用课程结构：

当前固定 **Java 21、Spring Boot 3.4.13、MyBatis-Plus 3.5.17、MySQL 8、Redis 7、JJWT 0.13.0**。Spring Framework／MVC 为 Boot 管理的 6.2.x。参考课程的业务思路和分层，依赖版本、Jakarta 包名和配置前缀以本项目为准。

实现 DTO 校验和拦截器时使用 `jakarta.validation`／`jakarta.servlet`；Redis 配置使用 `spring.data.redis.*`，MyBatis-Plus 配置使用 `mybatis-plus.*`。Boot 3 的 MockMvc 配置位于 `org.springframework.boot.test.autoconfigure.web.servlet`，JSON 的 ObjectMapper 使用 Jackson 2 的 `com.fasterxml.jackson` 包。JWT 签发／解析按 JJWT 0.13 的文档学习，不复制 0.11 的旧 API。

- sky-common：跨模块公共类型，已经提供 Result 与 PageResult。
- sky-pojo：未来的实体、请求 DTO、响应 VO；现在只有 package-info 学习提示。
- sky-server：应用启动、基础配置、健康检查与各业务空 package。

`SkyApplication` 启动应用，`EnvironmentController` 只返回环境信息。不要把 health 的成功等同于菜单或 Agent 完成。

默认 bootstrap 不连接数据库。你准备建表和 Mapper 时，使用 README 中的 local profile。先建立独立空库，不覆盖课程数据库。

## 1. 登录和当前身份

任务：

1. 设计员工点餐账户与食堂运营账户，决定合表还是分表。
2. 写登录 DTO，校验账号密码，安全保存密码。
3. 返回前端需要的 Session，并签发 JWT。
4. 写 JWT 拦截器，区分身份角色与接口权限。
5. 保存当前请求用户上下文，并确保正常、异常路径都清理。
6. 普通业务 Service 使用可信身份，不相信请求中的 userId。

验收：令牌有效能查本人信息；无令牌／失效令牌不能查业务；员工不能访问运营接口；请求失败后不会串用户。

思考：登录有效与拥有运营权限有什么区别？线程池复用时 ThreadLocal 不清理会发生什么？

## 2. 分类 CRUD：第一条完整调用链

按 `Controller → Service → Mapper → MySQL → Result` 实现分类列表、新增、修改与删除。

练习 Entity、DTO、VO 的区别，使用 MyBatis-Plus 的 BaseMapper、条件构造器和事务，需要自定义 SQL 时仍可写 XML。`@Mapper` 或 `@MapperScan`、实体的 `@TableName`／`@TableId` 以及分页拦截器由你添加；XML 放到 `sky-server/src/main/resources/mapper`。环境已经提供 JSqlParser 依赖，但没有替你实现业务 Mapper 或分页配置。

验收：管理端新增分类后可查询；排序正确；名称与类型非法时返回可读错误；关联商品的分类不能删除。

## 3. 菜品、套餐与库存模型

任务：商品 CRUD、起售停售、分页、标签、套餐组成、库存／可售数量、员工菜单目录。

先决定前端 itemId 的统一标识规则，再写表与 VO，避免菜品表 id=1 和套餐表 id=1 在购物车里冲突。

验收：运营可以编辑、停售；员工端只能买可售商品；套餐含真实菜品；套餐与单品共享库存时不会重复消耗或超卖；历史快照不随菜单变动。

## 4. 地址与购物车

任务：本人地址 CRUD 与默认地址；购物车查询和更新；合理的数量校验。

验收：员工不能修改或使用他人地址；价格由目录展示、成交由后端计算；删除不可售商品后可以继续结算。

## 5. 创建订单、支付与状态流转

任务：

1. 明确订单状态机、支付状态、取消和退款规则。
2. 下单时重新查商品，计算金额，检查并处理库存。
3. 同事务保存订单、明细、地址快照及必要幂等记录。
4. 实现学习支付或真实支付；采用模拟时明确标记。
5. 写订单列表／详情，限制本人范围。
6. 实现备餐完成、配送、自提完成及允许的取消。

验收：重复提交不生成重复订单；并发库存安全；非法跳转失败；取消仅回补一次；支付与取消竞争不会产生矛盾结果。

先使用前端演示观察流程，再切真实接口验证你写的 Service。

## 6. Redis、定时任务与 WebSocket

任务：

- 菜单缓存的查询回填、关联数据变更后的失效与有效期。
- 超时待支付订单扫描，基于预期状态更新，幂等回补。
- 单次 WebSocket 连接票据、身份绑定与事件推送。
- 通知只在事务成功后发送，并限定本人／运营权限。

验收：后台修改后员工列表更新；支付完成的订单不会被超时任务误关；用户只能收到自己的订单事件；断线后通过查询恢复真实状态。

## 7. 先写查询工具，再写 Agent

先用普通 Java 调用测试 `searchDish`、`querySetmeal`、`queryOrder`、`queryBusinessMetrics`、`queryDishSales` 的 Service 数据结果。

再实现：工具名称、描述、参数 Schema、权限、查询／执行类型、执行入口、结果裁剪、模型客户端和多轮编排。

验收：模型请求工具，后端实际执行，结果回传模型，后续查询基于真实 ID；超时或达到调用上限可以正常结束。

不要把前端 mock 中的规则回答复制进 Java 就称作真实 Tool-Calling Agent。

## 8. 推荐、经营分析与写操作确认

推荐：先后端筛选预算、可售状态、库存和已维护硬条件，再让模型排序；最终商品必须属于候选集合。

分析：后端计算营业额、趋势、销量、对比数据；模型组织观察与建议，缺数据时说明不足。

确认：生成待确认记录，绑定身份、参数、对象状态和有效期；用户确认后复验并执行；放弃和过期均不能执行；重复确认只生效一次。

验收场景参考 PROJECT_BRIEF.md 第 15、16 节。只有真实实现并完成验证后，才将这些能力写成“已完成”。

## 9. 推荐联调方式

1. 保持 mock 模式理解 UI 与流程。
2. 实现真实登录及基础店铺、分类、合并菜单接口。
3. 切 api 模式，以模块为单位逐步验证。
4. 页面显示 404 时查 API_CONTRACT.md，不给 Java 加假成功接口。
5. 新增后端功能时补业务规则测试；环境 SmokeTest 不能替代业务测试。
6. 更新 PROJECT_BRIEF.md 和 VERIFICATION.md，记录实际进度。

