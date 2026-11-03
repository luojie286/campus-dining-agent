# 套餐管理实现与验收

2026-11-03完成。数据库结构沿用infra/schema.sql，不执行建表或重建业务数据。

## 实现范围

- POST /api/admin/setmeal：新增套餐及组成，同事务保存，AOP填充审计字段。
- GET /api/admin/setmeal/page：名称、分类、状态组合筛选，数据库排序分页，批量读取组成。
- GET /api/admin/setmeal/{id}：编辑前读取详情。
- PUT /api/admin/setmeal：资料字段白名单、库存保护、组成事务更新、同值不更新审计。
- DELETE /api/admin/setmeal/{id}：停售与未完成订单引用检查，同事务清理组成，重复删除幂等。
- PUT /api/admin/setmeal/{id}/status：专用起售停售，校验组成及各级分类，不回写旧资料。

接口用token请求头，使用现有两种启用运营权限。套餐外部ID=数据库ID*2+1，组成菜品外部ID=数据库ID*2。
普通资料编辑中的stock只兼容接收，不写数据库；库存独立调整、批量删除不在本次实现范围。
零库存允许起售展示，实际可售量还受各组成菜品库存、状态及分类限制。

写操作按分类ID、菜品ID、套餐行、关系行锁定；锁定后核对初读的分类和组成集合，变化返回409。
未来订单写入也必须遵守相同资源加锁协议。当前删除沿用扫描未完成订单库存快照的实现，大数据量时需要专项优化；本轮没有进行并发压力测试。

## 后端接口测试

在backend目录，用JDK21和已有local数据库配置执行：

```powershell
$env:RUN_SETMEAL_DB_TEST='true'
$env:RUN_DISH_DB_TEST='true'
$env:RUN_DISH_PAGE_HTTP_TEST='true'
$env:RUN_SETMEAL_FRONTEND_TEST='true'
mvn.cmd -B -ntp test
```

-B使用批处理模式，-ntp关闭下载进度。环境变量仅启用本次终端的相应测试，不更改全局配置。
需要已有local连接凭证，以及已有启用管理员、启用普通运营和停用普通运营测试账号。
不打印、重置或提交账号密码；测试token由服务器针对现有运营签发，未验收密码登录。

四阶段套餐MySQL+MockMvc测试分别24、14、18、8项，共64项，均通过。
这些用例的写入及临时变更在测试事务结束回滚。
最终全量后端136项，135通过、1跳过；跳过旧的会保留永久菜品的DishHttpDatabaseTest。

## 前后端真实HTTP联调

SetmealFrontendIntegrationTest会启动随机端口后端和独立Vite代理，并运行前端setmeal.integration.test.ts。
它使用真实frontend/src/api/index.ts及HTTP封装，请求经过Vite代理、JWT拦截、Service、AOP、MyBatis及MySQL。
测试创建专属名称的分类、菜品和套餐，验证重名冲突、分页、详情、修改组成、库存保护、起售删除冲突、停售与重复删除。
finally通过API清理本用例创建的资源，后端再查询数据库确认无残留。临时会话文件和测试服务也会清理。
此用例会提交临时业务数据；不要在无测试写入授权的环境开启。

本次真实HTTP联调通过。前端默认测试60通过、2个需要显式开启的联调用例跳过；套餐联调用例已由上述后端测试独立开启并通过。
前端生产构建（包含vue-tsc类型检查）通过。

```powershell
# 在frontend目录，运行默认前端测试和生产构建。
npm.cmd run test
npm.cmd run build
```

套餐页已独立加载分类及全部分页菜品，不依赖店铺和合并菜单。
菜品目录加载失败、变化或不完整时明确报错；可售数量按当前目录估算，缺依赖时显示未知。
启停只提交status；编辑库存只读；真实接口失败不回退演示。

本轮未执行Swagger UI交互或真实登录后的浏览器逐按钮验收；真实接口链路与前端API联调测试不等同于这两种交互验收。
