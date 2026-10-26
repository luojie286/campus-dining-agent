# 园味 · 园区餐饮平台统一接口设计

版本：**2026-10-16**。本版统一运营后台、现有 Web 员工端、微信小程序和 Agent 的业务合同。

## 0. 文档边界、入口与固定决策

- 可重新导入 Apifox 的机器合同：[API_OPENAPI.json](API_OPENAPI.json)。完整方法、路径、请求/响应字段、参数约束、鉴权、示例以该文件为准。
- 按模块查看全部接口与实际实现状态：[API_MODULES.md](API_MODULES.md)。重新导入操作与迁移：[APIFOX_IMPORT.md](APIFOX_IMPORT.md)。持久化和前端缺口：[API_IMPLEMENTATION_GAPS.md](API_IMPLEMENTATION_GAPS.md)。
- 本次只改设计文档；没有修改 Java、Vue、数据库脚本、Nginx，没有执行 SQL、支付或外部账户操作。**设计完整不等于功能完成。**
- 已核对的源码入口：健康检查、运营登录、当前运营 ID、普通运营列表、Web 员工登录共 5 项。其余均是待实现合同；本次不重新宣称运行测试通过。
- 保留原有 52 个方法/路径组合及其 operationId，避免重新导入产生另一套接口。新增分页和专用操作接口时不改变旧接口的返回形状。
- 单园区单食堂、人民币、业务时区固定 **Asia/Shanghai**；客户端所在时区不改变数据库业务日。
- 运营身份分为 `ACCOUNT_ADMIN` 和 `OPERATOR`；点餐账户独立，JWT 的 `role` 仍为 `admin/user`。
- 管理页面只创建和管理普通运营账号；本版无公开新增管理员/升级管理员接口。管理员由受控初始化配置。
- 保留 Web 用户名密码登录，增加微信小程序登录。两端使用同一套用户/地址/购物车/订单 Service；同一个自然人不会因手机号相同而自动合并账户。
- 同时设计模拟支付和微信支付，服务端配置选择渠道；两个入口隔离，真实模式不能使用模拟入口改变订单。
- “导入商品浏览、导入地址”按小程序商品浏览和地址簿模块接入理解，**不表示 Excel 批量导入商品/地址**。图片上传另有接口；Excel 本阶段用于运营报表导出。
- 商品选购采用固定 tags 和数量，不含自选口味/SKU。现有 `dish_flavor` 是预留课程表，不把可选口味当成固定推荐属性。

## 1. 公共 HTTP、鉴权与错误合同

直连根地址 `http://127.0.0.1:8080`，所有业务路径已经包含 `/api`。Nginx 根地址只替换 host/port，不重复追加 `/api`。生产小程序使用配置好的 HTTPS 合法域名，WebSocket 使用 WSS。

业务请求/响应为 JSON；成功 HTTP 200：`{"code":1,"msg":"","data":...}`。无返回内容时 data=null。失败 `{"code":0,"msg":"可读原因","data":null}`。列表无数据返回 []，分页返回 `{total:0,records:[]}`，不返回 null 数组。接口响应示例是虚构结构示例，不是实际账号或数据库数据。

**例外**：Excel 成功响应是真正二进制 xlsx；微信通知成功 204 无 body，失败使用微信 `code:"FAIL"/message`，不套本站 Result。

| HTTP | 语义 |
| --- | --- |
| 400 | 参数缺失/格式/类型/长度错误，非法商品 ID，额外敏感创建字段 |
| 401 | 缺少、无效、已过期、已注销的凭证；当前登录账户不存在 |
| 403 | 登录身份无权，运营账号停用，操作管理员目标等禁止行为 |
| 404 | 资源不存在；用户请求他人地址、订单、会话、确认记录也按不存在处理 |
| 409 | 重复用户名、库存不足、非法状态、已被引用、幂等键冲突等业务冲突 |
| 429 | 催单冷却、请求频率或资源预算超限；返回 Retry-After 秒数 |
| 500 | 未预期服务故障；不输出 SQL、堆栈和凭据 |
| 503 | 微信/模型等外部依赖暂不可用；不得伪造成功 |

现有登录密码错误和 BusinessException 处理器可能产生 HTTP200/code=0；OpenAPI仅为两个已存在的登录接口保留该兼容分支。新冲突、权限等按表中状态实现；新增正确异常映射之前不能把文档状态视为已支持。

### 1.1 身份、实时权限与注销

- 请求头 `token: <本站JWT>`，不加 Bearer，不接收模型或客户端自填的角色/操作人 ID。
- `manager`：启用的 ACCOUNT_ADMIN，管理普通运营；`admin`：两种启用运营，共用商品/订单/营业/统计功能；`user`：当前点餐用户；`authenticated`：本站任一合法登录身份。
- 每次受保护请求校验签名、issuer、exp、身份、当前账号状态及目标归属。管理权限来自数据库当前角色，不能只信登录响应或 JWT 中的历史角色。
- 现有 `/api/admin/**` 拦截器不代表 `/api/user/**`、`/api/agent/**`、`/api/auth/**` 已被保护；后续必须补齐，不能把用户只读入口误当无须鉴权。
- 当前 ThreadLocal 只保存 ID；员工表与运营表 ID 可以相同。跨角色业务、异步任务、Agent 必须显式携带可信 role+id，不只传裸 ID。请求结束和异常路径清理。
- 新注销接口目标为撤销当前 JWT（jti 或摘要拒绝列表保存到 exp）并关闭相关 WS；当前 Web 退出仍只是本地删除。改密后撤销该账号所有现存凭证；需补充凭证版本/失效时间存储。无隐式永久 refresh token。
- 公共字段、任意 SQL、任意工具执行、手动跑调度任务、清所有缓存都不向客户端开放。

### 1.2 ID、金额与时间

- 菜品对外 itemId=`dish.id*2`（正偶数）；套餐=`setmeal.id*2+1`（大于1奇数）。数据库关联列存原始主键。category/user/employee/address/order ID 不转换。
- 菜单 id、购物车 itemId、明细 itemId、composition.dishId、销量 id、Agent 商品均使用同一外部映射。路径 kind 与奇偶不匹配400。
- 所有响应数字 ID 在 JavaScript 安全整数范围；新建兼容 `id=0` 只是占位，服务端生成实际 ID，不能用于更新/购物车。
- HTTP 金额为人民币元 JSON 数字，BigDecimal 精确到分；微信边界转换为整数分并核对币种CNY。客户端不指定成交金额、商户号、收款人或 openid。
- 时间戳带 ISO8601 偏移，例如 `2026-10-16T12:00:00+08:00`。日期是合法真实日历日，按 `[begin日00:00, end次日00:00)` 查询；不使用结束日23:59:59拼接。

### 1.3 分页、查询和重复请求

- page缺省1、pageSize缺省8且1～100，非法400；total是全部匹配数，超出最后页返回空records和原total。
- name/username/name类查询trim，空串等同未筛选；关键词最多100字符，绑定参数，`%/_`作为普通字符，不拼接SQL。
- 分类 sort/id 升序，运营账号 id 降序，菜单 category.sort/category.id/外部itemId 升序，地址默认优先/id降序，订单 createdAt/id 降序。合并菜单先合并筛选排序再分页。
- 新下单、购物车增量添加、微信预支付、退款重试要求 `Idempotency-Key`，16～64位字母数字下划线连字符。一次操作重试复用键，新操作用新键。
- 服务端按身份+方法+路径+键保存规范化业务请求摘要和结果，至少24小时；同键不同参数409、执行中409、成功重试返回原结果。不能只用“购物车内容相同”判重。幂等记录与业务写入必须具备可靠原子边界。
- 状态设置、整车替换、删除等按目标结果幂等；重复成功不重复事件、推送、退款或退库。旧前端缺稳定下单键属于待适配，不静默退化为无幂等保证。

## 2. 运营账号与公共字段自动填充

### 2.1 新增、分页、编辑、启停

核心接口：POST/PUT `/api/admin/employee`；GET `/api/admin/employee/page`、`/list`、`/{id}`；PUT `/{id}/status`。全部要求manager且目标仅OPERATOR。两种运营均可用本人 `/current`、`/me`、`/password`。

创建输入：`{id:0,username,name,phone,status,initialPassword}`。username/name trim后1～30字符，phone为11位大陆手机号，status=0/1，页面默认1。initialPassword为8～64字符且UTF8≤72字节，不trim，BCrypt保存；“确认密码”只在客户端校验，不提交。手机号不是登录名，不要求唯一；用户名在当前运营表唯一，按数据库现有不区分大小写的排序规则判重，唯一索引兜底并映射409。

服务端固定accountRole=OPERATOR，创建/修改请求禁止提交accountRole、password散列、审计字段；额外敏感字段400。创建id=0不是实际主键。返回安全Operator VO，新增accountRole，不返回密码/散列/初始密码/token。

更新以id指定普通运营，name/phone必填；username可选兼容旧表单，传入必须等于原值。status可选兼容旧表单，省略保持；新页面状态按钮使用独立状态接口。资料更新不能改密码或角色。不存在404，管理员目标403；无账户删除接口，使用停用保留历史审计。

管理员忘记普通运营的初始密码时，使用 POST `/api/admin/employee/{id}/password/reset`，请求 `{newPassword}`；仅manager和OPERATOR目标，撤销目标已有凭证/WS，记录操作人，不返回密码。它与本人验证旧密码的/password分开；普通资料编辑不承担密码重置。

`/page`支持page/pageSize/name/status，name匹配姓名或账号；`/list`保留数组形状作为兼容，只返回普通运营，不能直接把数组改成分页对象。当前后端只实现/list，分页及创建等仍待开发。

### 2.2 自动填充

公共字段填充是内部数据写入机制，不新增“填充字段”HTTP接口。创建时写createTime/updateTime/createUser/updateUser；更新时只更新updateTime/updateUser，保留创建字段。根据角色和值来源选择实际表字段，不给无对应列的表强行填运营ID。

时间使用统一业务时钟；操作人来自可信上下文；系统超时任务使用明确系统来源，不伪装某个在线管理员。MyBatis-Plus MetaObjectHandler或现有AOP只选一种权威填充方式；自定义SQL/批量更新同样写审计字段，不能绕过。客户端响应中出现的createdAt/updateTime不代表可在请求中覆写。

## 3. 分类、菜品、套餐、图片与库存

分类有创建/编辑/删除/list/page/status；type1菜品/2套餐，名称唯一，sort非负。有关联商品不许删、不许改type；禁用分类不批量篡改商品自身status，但员工目录/下单不可售。

商品保存结构继续使用MenuItem形状。name≤50、description≤300、price0.01～9999元、stock非负整数、固定tags不重复且辣度互斥；art为展示插画键；image是可选URL。菜品composition=[]，套餐至少1种菜品、最多100种、不重复，每种quantity1～99，不支持嵌套套餐。套餐整体售价独立于组成价总和。

每类商品有POST/PUT、GET page/详情、PUT /{id}/status、PUT /{id}/stock、DELETE /{id}和POST /batch-delete。批量最多100个，全部校验后同事务操作，不部分成功。

- 起售检查分类、目标存在及组成合法可售；停售组成菜品或禁用其分类会使依赖套餐实际不可售，不暗中改变套餐自身status。
- POST接初始stock；普通PUT中的stock只作旧表单兼容而忽略，返回数据库当前stock。独立stock接口用expectedStock原子比较更新，冲突409；它只检测数值冲突，不是版本/完整库存审计。
- 套餐实际可售量=min(套餐自身stock, 各组成菜品stock/份数向下取整)，还要校验各级分类和售卖状态。整车聚合共享菜品需求，不能逐行各自认为库存足够。
- 删除要求停售、无套餐引用、无未完成订单明细或库存快照引用；历史订单名称/价格快照保留。删除套餐同事务清组成，不能级联破坏订单。
- 客户端页面的status按钮逐步改专用接口，旧整对象PUT仍走同一状态校验。菜单返回商品自身stock，不把套餐可售数量冒充独立stock。

图片上传为POST `/api/admin/common/upload`，multipart file，JPEG/PNG/WebP，实际内容校验且≤5MiB；随机服务端文件名，拒绝客户端路径和SVG/可执行内容。商品填写本服务上传或受控CDN的URL，不根据任意客户端地址触发服务端抓取；art继续作为无图展示。无商品批量Excel导入接口。

## 4. 店铺营业与菜单缓存

GET `/api/admin/shop`、`/api/user/shop`；PUT `/api/admin/shop`输入`{open:boolean}`，两种运营均可。单食堂无多租户门店参数；营业时间是展示文本，不自动排班。

休息时可浏览/管理购物车，禁止创建新订单；已创建订单仍按截止时间支付和履约，不能打烊就删除订单或退款。

Redis缓存为服务端内部能力，不让客户端传缓存键/清缓存。缓存键区分用户可售视图、类型、分类、关键词、分页等维度；采用TTL、空结果短TTL、单键回填协调，Redis故障可回源数据库并记录告警。数据库事务提交后再失效；分类、菜品、套餐组成、状态、库存变动都影响关联缓存。对跨多种菜单缓存建议使用目录版本命名空间，避免漏删，更新版本只能在提交后执行。

缓存允许短暂最终一致，下单始终查数据库并在事务中检查价格、状态、库存；不能用缓存保证防超卖。图片上传本身不等于目录已修改。

## 5. 微信小程序、登录与商品浏览

小程序不是单独一套订单系统。wx.login取得code后POST `/api/user/wechat/login`，服务端调用微信code2Session，以appid+openid唯一关联账户并签本站role=user JWT。首次身份可建点餐用户，绝不建运营账号。不要求从昵称/手机号猜测园区员工身份；当前基线是可自注册点餐用户，不声称已接企业员工资格认证。

AppSecret、微信session_key、支付私钥只留服务端，不下发前端、不写日志。unionid可能不存在，不能作为唯一必填标识。code无效/重复400，依赖故障503；微信凭据错误不回退生成演示用户。

保留Web `/api/user/login`。若要使用原Web账号，小程序先输入Web账号密码取得本站token，再带新wx.login code调用 `/api/user/wechat/bind`；微信身份已绑定其他user时409，不按相同手机号自动合并，不搬迁订单/地址/购物车。已自动创建了另一个微信用户的情况明确冲突，不能隐式覆盖历史归属。

GET/PUT `/api/user/me`读取本人安全资料、修改显示姓名；POST `/api/user/wechat/phone`用用户授权getPhoneNumber得到的动态code换手机号，它与登录code不同。拒绝手机号授权不伪造资料，可在地址中填写本人收餐电话。自提订单若本人资料没有可用联系电话则400，要求先完善信息或选择配送；不得创建无联系信息的自提单。

商品浏览沿用 `/api/user/category/list`、`/dish/page`、`/setmeal/page`、`/menu/page`和各自`/{id}`详情；先登录，客户端不传userId。允许展示自身起售但库存0的商品，禁止下单；停售/自身分类禁用详情404。套餐组成缺货/停售时显示不可售，以服务端最终校验为准。

## 6. 地址簿与购物车

地址：list、详情、创建、更新、删除、/{id}/default；全部仅本人。他人资源404。consignee1～30、phone11位、building1～50、detail1～100；最多20条；isDefault布尔。兼容请求userId但忽略，使用当前身份。锁当前user行保证默认变更串行、最多一个默认。删除默认地址后不自动选另一个；订单保存地址快照不被后来修改。

购物车统一外部itemId，每人最多100种商品、每项1～99；无SKU/口味拆行，不提前预占库存。GET `/shoppingCart/list`返回CartLine[]，空[]，失效商品保留可移除。

- 旧Web：PUT `/shoppingCart`全量替换`{lines:[...]}`；空数组清空，全部校验后同事务替换，失败不破坏旧车。
- 小程序：POST `/shoppingCart/add`增量合并（要求稳定键）、PUT `/shoppingCart/{id}`设置目标数量、DELETE `/{id}`移除、DELETE `/clean`清空。
- 路径中的id是商品外部itemId，不是购物车行主键；静态/clean必须优先匹配，不能误当数字id解析。
- 所有购物车写入和下单锁同一user行，再按固定商品锁顺序，避免空购物车/全量替换与下单交叉。全量替换多端后到覆盖是显式限制；增量操作数据库原子合并。
- 数量增加校验整车共享库存；停售/缺货不阻止DELETE或清空。展示价来自菜单，成交价服务端重算。

## 7. 下单、订单状态和内部定时任务

POST `/api/user/order/submit`：`{fulfillment,addressId?,remark}`，remark≤300，配送必须本人有效地址；自提从本人资料和店铺自提点生成收餐快照。强制Idempotency-Key，客户端不传金额/状态/userId。

服务端检查营业和整车 → 固定顺序锁套餐/组成及菜品 → 重算金额、扣库存 → 同事务保存订单、成交明细、不可变inventory_snapshot、地址快照、初始事件 → 清本人购物车。失败全回滚。订单号唯一，不只依赖当前毫秒。

| 数据库status | API状态 | 正常下一步 |
| --- | --- | --- |
| 1 | UNPAID | 支付到PREPARING，未支付取消到CANCELLED |
| 2 | PREPARING | 备餐完成到对应READY状态 |
| 3 | READY_DELIVERY | 开始配送到DELIVERING |
| 4 | DELIVERING | 完成到COMPLETED |
| 5 | COMPLETED | 终态 |
| 6 | CANCELLED | 终态 |
| 7 | READY_PICKUP | 自提完成到COMPLETED |

没有单独待接单状态：本版支付成功即进入制作中，不额外设计与当前状态冲突的“接单”接口。备餐、配送、完成全部运营操作；定时任务不伪装用户确认送达。

用户仅取消本人UNPAID；运营可取消UNPAID/PREPARING/READY_DELIVERY/READY_PICKUP，不能普通取消配送中和已完成订单。重复目标成功不重复事件；非法跳转409。未支付取消按原snapshot退库一次；已支付默认不退食品库存。

内部任务（非公开HTTP）：每分钟分批扫描超过15分钟支付截止的UNPAID；MOCK条件关闭退库；WECHAT先查单/关单，确认未支付关单后才释放。外部结果未知时保留待核对，不因超时就误判未支付。另有支付/退款补偿扫描、过期票据和确认记录清理。每次按预期旧状态、唯一业务号和事务幂等；多实例不能重复生效。

## 8. 模拟支付、微信支付与退款

服务端选择MOCK或WECHAT运行渠道，每个订单记录实际支付渠道；不能根据客户端参数切换。旧POST `/user/order/{id}/pay`明确是MOCK入口，真实模式409；其成功只是学习模拟，不产生真实扣款。

真实流程：POST `/user/order/{id}/payment/wechat`（稳定键）→ 服务端按本人微信身份及订单金额预下单 → 返回appId/timeStamp/nonceStr/package/signType/paySign/expiresAt → 小程序wx.requestPayment → GET `/{id}/payment`查本站结果。前端success不等于后端付款完成。

POST `/api/payment/wechat/payment-notify`和`/refund-notify`为微信服务端回调，无本站JWT但必须验证原始body签名、时间/重放、平台证书或公钥、解密resource，再核对商户、appid、业务号、币种和金额。事件ID/交易号幂等，持久接收到通知后204无body；失败用微信格式4xx/5xx，不能统一Result。异步消费必须先可靠落库再应答，不能仅放内存队列就204。

用户关闭、超时关闭和支付成功竞争必须串行化订单本地状态并协调外部关单；已取消后发现真实付款成功，进入退款补偿，不复活订单或丢失款项。外部调用不长期占用数据库锁；使用支付尝试/关单状态与重试协调，不把跨微信MySQL操作假装成单数据库事务。

payStatus保留UNPAID/PAID/REFUNDED三值；新增可选refundStatus=NONE/PROCESSING/SUCCESS/FAILED。已付运营取消时创建全额退款记录并置CANCELLED；处理中仍PAID，完成才REFUNDED。取消结果不谎称退款完成。GET `/user|admin/order/{id}/refund`恢复状态。POST `/admin/order/{id}/refund`仅重试已取消的历史已付订单，稳定键+唯一商户退款号，不重复退款；已完成订单普通退款本版不开放，部分退款也不开放。

微信真支付需要小程序、商户关系、合法HTTPS通知域名、证书/公钥和密钥等外部配置；没有这些条件仍可先完整实现模拟流程，不能在配置缺失时伪造真支付成功。

官方协议参考：[微信小程序登录](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)、[JSAPI/小程序下单](https://pay.wechatpay.cn/doc/v3/merchant/4012791897)、[普通支付成功通知](https://pay.wechatpay.cn/doc/v3/merchant/4012791861)。本站接口是对上述外部能力的业务封装，不原样透传供应商全部字段。

## 9. 来单提醒、催单与WebSocket

POST `/api/auth/ws-ticket`用本站token取得30秒单次票据；连接 `/ws/orders?ticket=...`，服务端原子消费并绑定role+id+token。断线重连重新申请；注销、停用接口主动关闭相关连接，握手和每30秒存活检查复验凭证及账户状态，到exp关闭，不能只在初次握手检查。小程序wx.connectSocket，生产WSS；Nginx需代理Upgrade/Connection。

事件统一eventId/type/orderId/orderNumber/occurredAt，可选status/urgeCount。旧客户端已有orderNumber和type仍保留。

- NEW_ORDER：付款成功进入PREPARING后发给运营；未支付订单创建不作为备餐来单提醒。
- ORDER_STATUS_CHANGED：事务提交后发给订单本人及运营，用户收不到他人事件。
- ORDER_URGED：仅运营收到客户催单；不更改履约状态。

POST `/api/user/order/{id}/urge`只允许本人已付未完成的制作/待配送/配送中/待自提状态。间隔≥60秒，冷却429并返回Retry-After；每订单最多5次，达到总上限后409，终态/未支付409。成功返回remaining，第五次remaining=0并省略nextAllowedAt，不承诺总上限后等待即可再催。持久计数与最后时间原子更新后推送，防并发绕过。

客户端按eventId去重，音效受浏览器/小程序授权限制，不承诺自动播放。WS可能断线/重复/丢失，HTTP订单查询是事实恢复入口，不宣称恰好一次。需要可靠补发时配事务发件箱；本版数据库扩展清单已预留。

## 10. 统计口径、工作台与Excel

全部只开放两种运营。范围型统计不传begin/end默认最近7自然日；只传一端400，最多90日，含两端，趋势缺日补0。所有模块/Agent/导出调用同一统计Service，不能各自算一套。

### 10.1 营业额 `/api/admin/report/revenue`

- grossPaidAmount：支付成功时间落在范围的历史实付总额，含后来退款的订单，不叫净利润。
- refundAmount：退款成功时间落在范围的实际退款额，与原支付日期无须相同。
- netCashAmount=grossPaidAmount-refundAmount，可能为负；不是利润。
- paidOrders：按支付成功时间统计的历史支付订单数，同一订单一次；退款不抹去历史支付。
- trend逐日按同口径；模拟与真实数据隔离运行，模拟报表明确标记模拟口径。

### 10.2 用户 `/api/admin/report/users`

- newUsers按用户createTime在范围内，排除employee表；停用不删除注册事实。
- totalUsers为截至end日结束的累计注册用户数；微信/Web已明确绑定同一user时只计一次，未绑定不按手机号猜测合并。
- orderingUsers按范围内创建订单的不同user去重；payingUsers按范围内成功付款的不同user去重，后续退款不抹去历史付款行为。
- trend各日相同定义，totalUsers是截至该日累计，其余为当日去重/新增，不能把每日人数相加冒充区间去重人数。

### 10.3 订单 `/api/admin/report/orders`

- createdOrders及counts按创建日期范围和当前履约状态；cancelledOrders/current completedOrders为该创建范围当前取消/完成的数量。
- validOrders为该创建范围当前已付且未取消、未退款订单数；completionRate=completedOrders/createdOrders*100，无创建时0。
- paidOrders按付款时间范围，是另一时间口径；trend每字段按对应口径，paidOrders不必等于当天创建订单中的已付数。
- 所有状态键补0，百分比0～100保留两位小数；不能将pay_status=REFUNDED订单从历史支付总数中抹掉。

### 10.4 销量 `/api/admin/report/sales`

付款时间在范围、当前未取消且未退款的有效成交明细；按外部itemId汇总quantity和成交单价*数量，历史名称取最近一条入选快照。套餐按套餐计，不把其组成重复加到销量收入。kind可选dish/setmeal；topN默认10、1～100；销量降序,id升序。

### 10.5 兼容概览和工作台

旧 `/api/admin/report/overview`保留Dashboard字段：revenue=历史支付总额，paidOrders/averageAmount同支付时间；orderCount/counts/completedOrders/cancellationRate按创建时间，trend.orders按支付时间，sales同有效销量。metricNote明确混合时间口径。当前mock和界面的“不含模拟退款/创建订单趋势”说明需后续适配，不能反过来改变统一统计定义。

新 `/api/admin/workbench?date=...`默认今天，不能未来日；营业收入/支付数按当日付款，创建/完成按当日创建订单及当前状态。**pending统计全部日期未完结订单**，不能近7天过滤漏掉旧待办。菜品/套餐上下架数量按当前原始状态计数，不与实际可售库存混淆。报表历史查询是当前数据库视图，不声称不可变财务历史快照。

### 10.6 导出 `/api/admin/report/export`

type=summary/orders/sales/users，begin/end必填，最多31日。成功返回真正xlsx，MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，Content-Disposition带可下载UTF8文件名，不套Result。

summary多表展示营业/退款/订单/用户聚合；orders按创建范围，一订单一行，无手机号和详细地址；sales按销量口径；users按日聚合。最多10000数据行，超限409且不静默截断。文本写字符串单元格防公式注入，金额写数值；无token/密码/openid/原始用户资料。错误仍JSON Result。原Web浏览器CSV导出保留为旧功能，不能把CSV改后缀称作Excel。

## 11. 智能Agent设计

基础业务实现后再接Agent，不提前伪造模型能力。HTTP入口：POST `/api/agent/chat`，本人会话分页/消息/删除，确认action查询/confirm/cancel。chat `{message,sessionId}`，空串新会话，message trim后1～1000字；响应保留sessionId/content/items/orders/metrics/traces/action。HTTP不暴露任意toolName+params执行接口。

会话归role+id，Redis TTL24小时，至多保留最近30条可见消息；每轮输入历史及工具结果文本合计最多32000字符，单次工具结果最多8000字符/20条，裁剪时保留身份范围、口径、单位和完整性标记。到期404并重新开始，不恢复不存在的历史。删除会话使其待确认action失效，不回滚已经执行的业务。每轮最多5次模型请求、8次工具执行、15秒总预算，单次模型请求最多5秒、工具最多2秒且均不得越过剩余总预算；超限/超时可读失败，不伪造成功trace。上述上限集中配置，不由模型临时修改。

| 工具 | 谁可用 | 事实来源及边界 |
| --- | --- | --- |
| searchDish / querySetmeal | 当前登录身份 | 同一目录Service，预算/分类/状态/库存/标签硬过滤；候选外商品禁止输出 |
| queryOrder | user本人、运营授权范围 | 订单Service按可信身份限制，模型不提供身份参数 |
| queryBusinessMetrics / queryDishSales | 两种运营 | 同一统计Service，保留范围、单位、口径和截断说明 |
| prepareCancelOrder | 本人或运营允许范围 | 第一版仅未支付订单，生成确认记录，不立即取消 |
| prepareSetmealStatus | 两种运营 | 准备套餐上下架，确认时复查分类/组成和目标状态 |

推荐先确定性过滤再模型语义排序，最后校验商品ID/价格仍属于工具候选；未知口味/过敏原不保证，空候选不编造、不暗中放宽预算。经营分析区分事实、观察与推测，缺对比数据不编环比。

写操作卡片有效5分钟，绑定身份、会话、白名单工具、规范化参数、目标预期状态/版本和影响摘要；参数生成后不可由客户端覆盖。确认时锁action和目标，复查最新权限及对象，再同事务执行业务并保存结果。重复成功返回原结果；取消/过期/目标变化409；确认与撤销原子竞争。GET action用于响应丢失恢复。

Agent不提供创建账号、授予管理员、修改密码、直接支付/退款、任意数据库操作工具。商品描述/用户备注/工具结果均是数据，不能覆盖系统权限。工具结果使用专用裁剪DTO，不把完整Order实体/VO中的电话地址整体传给模型；前端卡片由后端按当前身份另行组装。trace仅安全摘要，不展示或保存隐藏思维链。模型密钥仅后端外部配置。

## 12. 实现顺序、合同验收与版本纪律

1. 运营角色与认证完善 → 人员CRUD/分页/状态/改密及初始密码前端。
2. 公共字段填充 → 分类 → 菜品 → 套餐/组成 → 店铺/图片。
3. 小程序登录/身份绑定及资料 → 浏览目录 → 地址 → 购物车。
4. 下单库存/稳定键 → MOCK支付和状态 → 微信渠道/查单/通知/退款及补偿。
5. 缓存、超时、WS来单/催单 → 同口径统计/工作台/xlsx。
6. 同一Service工具封装 → 真模型Agent → 确认与会话管理。

所有阶段验证：角色越权/他人资源、DTO字段白名单、有效分页/空结果、重复用户名、普通运营不能创建管理员、失效JWT/停用即时拒绝、共享库存并发和回滚、支付关单竞争、重复通知/退款、快照不变、统计日边界/退款跨日、导出类型/超限、Agent越权/超时/确认重放。

本版先定义完整外部接口，后续按合同逐项实现，不为每个实现步骤重命名URL或改变返回形状。新增可选响应字段保持兼容；修改必填请求/状态含义等破坏性变化必须做版本化迁移，不能静默覆盖旧语义。微信供应商协议以接入时官方要求为准，供应商变化由后端适配层吸收，本站业务接口保持稳定。
