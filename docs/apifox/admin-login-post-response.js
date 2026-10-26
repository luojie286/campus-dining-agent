// 复制到 POST /api/admin/employee/login 的「后置操作 → 自定义脚本」。
// 登录与业务请求必须选择同一个 Apifox 环境；不要在公共后置操作中执行。
// 先清除旧值，避免登录失败后继续携带之前的账号凭证。
pm.environment.unset('apiKey');

const result = pm.response.json();
if (pm.response.code !== 200 || result.code !== 1) {
  throw new Error('运营登录失败，未保存 apiKey，请检查登录响应');
}

const session = result.data;
if (!session || session.role !== 'admin' ||
    !['ACCOUNT_ADMIN', 'OPERATOR'].includes(session.accountRole) ||
    typeof session.token !== 'string' ||
    !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(session.token)) {
  throw new Error('登录响应没有有效的运营 JWT，请检查 data.token 和运营角色');
}

pm.environment.set('apiKey', session.token);
// 不输出 token；能否创建人员仍由后端校验实时账号权限。
