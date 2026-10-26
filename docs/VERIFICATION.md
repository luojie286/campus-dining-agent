# 环境与前端验证记录

工程：`E:\codex\Roger001`。最新后端环境更新：2026-10-10。

## 2026-10-10：Java 21 / Spring Boot 3.4 / MyBatis-Plus 环境迁移

按用户最终指定，当前环境固定为 Java 21、Spring Boot 3.4.13、MyBatis-Plus 3.5.17、JJWT 0.13.0。本机实际 Java 与 Maven JVM 都检测为 Zulu 21.0.12.1；Maven 为 3.9.16。Spring Framework／MVC 6.2.x、Spring Data Redis、Tomcat 10.1 和 Validation 由 Spring Boot BOM 管理。服务端目标 MySQL 8、Redis 7；Compose 已配置为 MySQL 8.4、Redis 7.4，未连接或启动真实数据库服务。

正式版本已通过 [Spring Boot 3.4 官方要求](https://docs.spring.io/spring-boot/3.4/system-requirements.html)、[MyBatis-Plus 官方安装说明](https://baomidou.com/getting-started/install/)、[JJWT 发布记录](https://github.com/jwtk/jjwt/releases/tag/0.13.0) 及 Maven Central 对应系列元数据核对。

迁移内容：

- Maven 使用 Java 21 的 release，并用 Enforcer 拒绝其他 JDK 主版本；启动脚本也检查 Java 21。
- 使用 Boot 3.4 的 Web 与测试 starter，Spring Framework／MVC 由 Boot BOM 统一管理。
- 移除原生 MyBatis Starter，改用 MyBatis-Plus 的 Boot 3 Starter；提供实体注解与 JSqlParser 依赖。
- bootstrap 排除数据源、MyBatis-Plus 和 Redis 自动配置。
- local Redis 前缀改为 `spring.data.redis.*`。
- local 的 Mapper XML／别名／驼峰配置前缀改为 `mybatis-plus.*`。
- 保持未实现业务接口的 404；处理 MVC 6.2 的 NoResourceFoundException，避免被泛化为 500。
- 新增环境测试检查 bootstrap 不创建数据库／Redis 连接，以及 local Redis 环境变量正确绑定。
- 更新 README、学习路线与项目约定。未更改用户参考工程、D:\Nginx 或系统全局 Java 设置。

本次实际验证结果：

| 检查 | 结果 |
| --- | --- |
| `mvn.cmd -B -ntp clean verify` | 三模块 BUILD SUCCESS，4 项环境测试通过 |
| 编译目标 | javac release 21；Result 类的字节码 major version 为 65 |
| 构建 JDK 检查 | 在独立子进程临时选择 Java 17 后，Maven validate 被 Enforcer 拒绝 |
| 启动 JDK 检查 | Java 17 下启动脚本明确拒绝；Java 21 下 `start-backend.ps1 -SkipBuild` 成功启动 |
| 实际运行 | Zulu 21.0.12.1、Spring Boot 3.4.13、bootstrap、127.0.0.1:8080 |
| `GET /api/health` | HTTP 200，code=1，javaVersion=21.0.12.1，springBootVersion=3.4.13，businessImplemented=false |
| `GET /actuator/health` | HTTP 200，status=UP |
| `GET /api/admin/dish/page` | HTTP 404，返回接口未实现说明；没有假业务成功响应 |

4 项测试分别验证当前 Java／Boot 版本及健康响应、未实现接口 404、bootstrap 不创建数据源／Redis 连接、local Redis 环境变量绑定。

对最终可执行 JAR 检查得到 Spring Framework／MVC 6.2.15、Spring Data Redis 3.4.13、Tomcat 10.1.50、Jackson Databind 2.18.5、MyBatis-Plus 3.5.17、JJWT 0.13.0，未混入 Spring Boot 4 的模块。MySQL JDBC 驱动为 Boot 管理的 9.5.0，这是驱动版本，数据库服务器目标仍为 MySQL 8.4。

MySQL／Redis 的实际服务、业务表、业务事务和真实 Agent 仍未验证；本次没有启动 Compose 容器。前端没有修改，未重复宣称新的前端验收。下面的 2026-10-08 内容是旧环境的历史记录，不代表当前使用 Java 17 或 Boot 2.7。

---

## 2026-10-08：原前端交付与旧后端环境记录

以下版本号、两项后端测试和截图保留当时实测值；旧后端环境未满足用户 Java 21 要求，已经被上面的新环境替代。前端验收仍属历史结果，本次不把旧截图冒充新环境验证。

## 交付范围

员工端、运营端和 Agent 页面的前端交互已实现，默认使用明确标记的本地演示数据。后端仅提供 Java 环境骨架和健康接口。没有替用户实现业务实体、数据库表、Service、Mapper SQL、登录鉴权、订单任务、WebSocket 服务或真实 Agent。

用户提供的前端、`E:\codex\l4` 和课程介绍只作为架构与页面参考，没有被修改。

## 实际工具环境

| 工具 | 检测结果 |
| --- | --- |
| Java | Zulu 17.0.20.1 |
| Maven | 3.9.16 |
| Node.js | 24.12.0 |
| npm | 11.6.2 |
| 前端 | Vue 3.5.43、TypeScript 5.9.3、Vite 7.3.7 |
| 后端 | Spring Boot 2.7.18，Java 17 |
| MySQL | PATH 中检测到 mysql 客户端；未验证服务器运行或连接 |
| Redis / Docker | PATH 中未检测到对应命令；未安装、启动或接管服务 |

依赖版本以 Maven POM、package.json 和 package-lock.json 为准。安装结束时 npm audit 报告 0 项漏洞，这只是当次依赖审计结果。

## 构建和自动测试

| 检查 | 命令 / 行为 | 结果 |
| --- | --- | --- |
| 前端类型与生产构建 | `npm.cmd run build`（先运行 vue-tsc） | 通过，产物位于 frontend/dist |
| 前端规则测试 | `npm.cmd run test` | 2 个测试文件，10 项通过 |
| 后端环境测试 | `mvn.cmd -B -ntp test`；打包时再次执行测试 | 2 项通过 |
| 后端可执行 JAR | `mvn.cmd -B -ntp package` | 三模块 BUILD SUCCESS，已生成重打包 JAR |
| 后端实际启动 | 运行 sky-server-0.1.0-SNAPSHOT.jar | bootstrap 启动，127.0.0.1:8080 |

前端测试覆盖组成菜品约束、共享库存需求、非法数量与终态、员工订单隔离、预算候选筛选、确认前不写入、确认幂等、确认身份绑定、过期拒绝、放弃确认后失效、订单库存回补一次及商品快照。它们验证的是**前端演示数据层**。

后端两个测试只验证健康检查和业务接口未实现。它们不证明数据库、鉴权或真实模型功能已经完成。

## 实际 HTTP 和页面连接

服务启动后直接读取本机 HTTP 接口，得到：

```text
GET /api/health                HTTP 200，code=1
  application=campus-dining
  javaVersion=17.0.20.1
  springBootVersion=2.7.18
  profiles=[bootstrap]
  businessImplemented=false

GET /actuator/health           HTTP 200，status=UP
GET /api/admin/dish/page       HTTP 404
```

前端“接口与环境”页通过 Vite 的 `/api` 代理读取健康检查，实际显示“可连接”和“环境骨架 · 等你实现”。本机 PowerShell 的 HTTP 代理会影响回环请求，终端验证使用 `Invoke-RestMethod -NoProxy`；浏览器页面代理连接正常。

## 浏览器流程验收

在 Codex 内置浏览器中操作本地前端，使用虚构的演示账号与数据。

| 场景 | 实际观察结果 |
| --- | --- |
| 运营登录与工作台 | 登录后展示近七天收入、已支付订单、趋势、销量和今日订单 |
| 菜品管理 | 新增测试菜品、编辑价格、删除确认成功；测试菜品已清理 |
| 套餐管理 | 编辑回填名称、价格、标签、两个组成菜品；保存成功 |
| 分类管理 | 分类记录回填和保存成功 |
| 运营人员 | 从分类页切换后正确展示运营记录；编辑回填和保存成功 |
| 员工登录与菜单 | 身份切换后进入员工菜单；套餐、不辣、30 元预算筛选成功 |
| 购物车与下单 | 元气鸡腿套餐单价 28 元、数量 2、自提、合计 56 元；创建订单后购物车清空 |
| 员工订单 | 详情显示商品快照、数量、总价和时间线；模拟支付确认后进入制作中 |
| 运营订单 | 同一订单依次通过确认操作进入待自提和已完成；时间线追加状态记录 |
| 点餐 Agent | 推荐返回符合条件的商品卡片与工具轨迹；页面明确标记规则模拟、未调用 LLM |
| 运营 Agent | 下架请求先显示“尚未变更”和确认卡片；确认后列表实际显示已停售；已重新起售 |
| 地址管理 | 编辑地址字段回填，保存成功 |
| 经营统计 | 今天与近七天日期切换后指标改变；CSV 下载成功 |
| 窄屏导航 | 抽屉导航、退出登录和身份切换可用；侧栏低高度时可滚动 |
| 页面宽度 | 桌面与窄屏 DOM 检查未出现整页水平溢出；表格保留局部横向滚动 |

验收创建的订单号为 `YW1791445370315`，已经在本浏览器演示中完成，保留作为流程样例。这些数据不在数据库中，也没有实际扣款。

CSV 由浏览器下载到其当前下载目录，文件名为 `园味经营统计_2026-10-08_2026-10-08.csv`。它由演示订单计算，不能作为真实营业记录。

验收中修复了 Vue 响应式对象直接 structuredClone 导致编辑弹窗打不开的问题；改为对普通业务记录进行 JSON 克隆。另修复了低高度侧栏底部按钮不可达和购物车收餐方式按钮标签问题。

## 页面截图

截图来自本次运行的页面，包含演示数据。桌面截图采用临时视口进行布局检查；浏览器缩放及面板截图可能裁剪边缘。检查结束已经恢复默认视口。

- [运营工作台](screenshots/admin-dashboard.jpg)
- [前端连接后端环境](screenshots/environment.jpg)
- [员工菜单窄屏预览](screenshots/employee-menu.jpg)

## 尚未验证 / 留给用户实现

- MySQL 与 Redis 的真实服务、密码、业务建表和连接验证。
- 真实登录、JWT 签名与验证、拦截器、ThreadLocal 和权限校验。
- 真实业务请求、数据库事务、库存并发、状态更新幂等、退款与支付。
- Redis 缓存、超时订单任务和后端 WebSocket 通知。
- LLM 模型接入、Function Calling、多轮编排、审计与服务端确认记录。
- 真实 API 模式的端到端联调、生产部署和跨设备同步。

前端已提供真实接口适配和通知客户端，它们需要按照 docs/API_CONTRACT.md 实现后端才能联调。演示模式的成功不是这些真实功能已经完成的证据。

## 复现方式

参照根目录 README.md 的两个启动脚本。默认前端访问 `http://127.0.0.1:5173`，后端健康检查访问 `http://127.0.0.1:8080/api/health`。

运营演示账号 `admin / admin123`，员工演示账号 `staff / 123456`。真实接口模式需要你实现登录并使用真实后端账号。
