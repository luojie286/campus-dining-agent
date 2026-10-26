# 重新导入 Apifox：2026-10-16 完整接口基线

## 导入哪个文件

导入同目录的 **[API_OPENAPI.json](API_OPENAPI.json)**，OpenAPI 3.0.3、版本2026.10.16。不要再导入旧副本。模块与权限目录见 [API_MODULES.md](API_MODULES.md)，详细规则见 [API_CONTRACT.md](API_CONTRACT.md)。

这次更新的是设计文档；只有5个已核对源码入口标注已实现，其余“待实现”是后续开发合同，导入不会生成后端功能。

## 在原项目中更新

1. 如已有自己维护的脚本、环境和用例，先导出一份 Apifox 项目备份。
2. 打开原项目的“项目设置 → 导入数据”，或左侧目录树“+ → 导入”，选择 OpenAPI/Swagger 文件，选择新版 API_OPENAPI.json。
3. 对方法+路径相同的接口，更新响应结构、请求结构、说明及示例。要完整采用新合同，使用覆盖已有接口或覆盖指定字段；智能合并可能保留你旧的说明/示例，应检查是否覆盖到accountRole等新字段。不要选择“保留两者”生成重复定义。
4. 数据模型和响应组件也要更新，尤其AdminSession、UserSession、OperatorCreateRequest、OperatorUpdateRequest、分页模型、支付/退款和统计模型。请求模型与响应模型分开维护。
5. 资源清理选择“不删除”，保留你单独创建的测试用例和其他资源。导入预览确认新模块和原有路径后再执行。
6. 本版保留原52个接口的方法、路径和operationId。源文件不包括你的实际账号密码、token、Nginx地址或环境变量，不会自动填真实凭证。

以上按可视化维护项目说明；采用OpenAPI规范文件维护的spec模式项目，应更新其规范源文件，界面可能没有同样的预览流程。[官方导入说明](https://docs.apifox.com/manual-import)、[冲突与覆盖设置](https://docs.apifox.com/import-settings)。

## 导入后统一检查

- 本地后端前置URL设 `http://127.0.0.1:8080`，接口路径已含/api。用Nginx时只改主机/端口为其根地址，不再额外拼/api。
- 鉴权为API Key请求头`token`，直接JWT，不加Bearer。登录/health无本站鉴权；微信通知不带JWT，但需要微信签名，不可当普通用户写接口。
- 接口标题区分“已实现/待实现”。这表示代码状态，不等于测试状态；运行通过后再维护自己的测试结果。
- Session为兼容共享模型；运营登录具体返回AdminSession，包含accountRole；Web/微信用户登录返回UserSession，无运营角色。
- list保持数组；新page返回`{total,records}`。不要把旧人员list改成分页响应再让原前端继续使用。
- 创建账号有initialPassword，无可填写accountRole；普通运营角色固定服务端决定。
- 新下单/增量购物车/微信预支付/退款接口带稳定Idempotency-Key。一次操作重试复用，同键不同参数409。
- Excel成功是二进制xlsx，微信通知成功204无body，不能为这两个接口套JSON Result断言。
- GET /api/user/login不是合同；登录是POST。PATCH不是本版接口方法；状态更新均明确PUT。

## 建议保存的测试用例

### 新增人员返回 401：检查登录凭证与预期响应

如果请求头显示 `token: {{apiKey}}`，先检查实际发出的请求；导出的模板仍显示变量名，并不能单独证明变量未替换。把字面量 `{{apiKey}}` 发给当前 JWT 拦截器会返回 `401 / 登录凭证无效或已过期`；过期、签名不匹配、格式错误的 JWT 也会被拒绝。

1. 选择当前测试环境。若实际服务运行在 8081，前置 URL 设置为 `http://127.0.0.1:8081`，登录和新增接口使用同一环境及服务。
2. 请求 `POST /api/admin/employee/login`，Body 为真实账号的 `{username,password}`。登录接口不携带本站鉴权。要新增人员，登录账号的 `data.accountRole` 必须为 `ACCOUNT_ADMIN`。
3. 登录接口的「后置操作 → 自定义脚本」粘贴 [admin-login-post-response.js](apifox/admin-login-post-response.js)。它在成功后把 `data.token` 写入环境变量 `apiKey`，失败时清除旧值；不要把它放在公共后置操作中。不要向他人发送真实 token。
4. 新增接口鉴权使用 API Key：位置 Header、名称 `token`、值 `{{apiKey}}`。如果已通过鉴权配置生成此请求头，删除手工重复添加的同名 Header。值为 JWT 本身，不加 `Bearer `、引号或大括号。检查是否存在同名临时/测试数据等变量覆盖环境变量。
5. 先调用 `GET /api/admin/employee/current` 验证 token，正常为 `200 / code=1`。再调用 `/list` 验证账号管理权限，最后发送新增请求。`current` 成功不代表具有新增权限；普通运营访问新增应为 403。
6. 在新增接口的响应定义中保留 200 成功响应，并确认已导入 401 错误响应：`code` 为整数且枚举为 0，`msg` 为字符串，`data` 允许 null。仓库的 OpenAPI 已有这两个响应，无需修改成功模型。若旧 Apifox 定义缺少 401，覆盖更新该接口及 ErrorResult 模型；若调试用例固定预期 200，成功用例应先修好 token，鉴权失败用例则单独选择/断言 401。

Apifox 的 `pm.response.json()`、`pm.environment.set()` 和 `unset()` 用法见[官方脚本 API](https://docs.apifox.com/pm-%E8%84%9A%E6%9C%AC-api-5580997m0)。脚本只负责保存服务器签发的 JWT，不会延长过期时间，也不赋予管理员权限。

当前 401 属于鉴权失败，JSON 请求体尚未进入新增逻辑。不要把后端拒绝访问改成 HTTP 200，也不要为了通过成功用例的断言而关闭 JWT 校验。

| 模块 | 至少覆盖 |
| --- | --- |
| 登录/权限 | 两种运营登录、普通运营list/page/create403、无token401、停用拒绝、本人current成功 |
| 账号 | 有效创建、缺密码400、重复用户名409、提交accountRole400、管理员目标403、分页越界空records |
| 分类/商品 | 类型错400、关联删除409、套餐组成无效、状态启停、旧stock不覆盖、独立库存冲突409 |
| 购物车/地址 | 他人资源404、默认地址并发、停售商品可移除、共享库存、增量稳定键重放 |
| 订单/支付 | 下单重放同订单、同键不同输入409、支付与关单竞争、模拟入口在真支付模式拒绝、真实通知验签/金额不符拒绝 |
| 催单/统计 | 间隔60秒429、最多5次、终态409、日期边界/跨日退款、用户区间去重、销量不重复拆套餐 |
| Excel/Agent | xlsx文件和Content-Type、超限409、无凭据泄露、Agent他人会话404、确认过期/重放/目标变化 |

真实微信通知验签需要供应商环境或受控测试夹具，不通过随便拼JSON就宣称验收。未实现接口404不是设计文件导入失败。

## 前端衔接

当前Web前端已有人员list、全量购物车、整对象商品PUT、模拟支付按钮、概览统计和CSV。新增分页、专用状态、初始密码、真实微信支付、报告xlsx、小程序和Agent历史需要逐项适配，见[实现缺口](API_IMPLEMENTATION_GAPS.md)。本次没有替你修改这些业务入口。
