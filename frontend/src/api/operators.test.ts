import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', '/api')
  vi.stubGlobal('localStorage', { getItem: () => null })
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 1, data: {} }), { status: 200 })))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

const operator = { id: 0, username: 'newuser', name: '新运营', phone: '13800000003', status: 1 as const, accountRole: 'ACCOUNT_ADMIN' as const }
describe('运营账号请求字段', () => {
  it('编辑回显通过ID读取详情，直接使用后端返回的最新记录', async () => {
    const current = { ...operator, id: 3, name: '最新姓名', status: 0, accountRole: 'OPERATOR' }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 1, data: current })))
    const { api } = await import('./index')
    await expect(api.operator(3)).resolves.toEqual(current)
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee/3', expect.objectContaining({ method: 'GET', body: undefined }))
  })
  it.each([403, 404])('详情HTTP %i失败明确报错，不回退列表或演示数据', async status => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '不能读取该账号' }), { status }))
    const { api } = await import('./index')
    await expect(api.operator(3)).rejects.toThrow('不能读取该账号')
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('编辑只提交姓名和手机号，不携带旧状态、用户名、角色或密码', async () => {
    const saved = { ...operator, id: 3, name: '新姓名', status: 0, accountRole: 'OPERATOR' }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 1, data: saved })))
    const { api } = await import('./index')
    const input = { ...operator, id: 3, name: ' 新姓名 ' }
    await expect(api.saveOperator(input, 'IgnoredPass123!')).resolves.toEqual(saved)
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ id: 3, name: '新姓名', phone: operator.phone })
    }))
    expect(input.status).toBe(1)
  })
  it.each([400, 403, 404])('编辑HTTP %i失败保留原始表单数据，不自动重试或回退', async status => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '资料保存失败' }), { status }))
    const { api } = await import('./index')
    const input = { ...operator, id: 3 }
    await expect(api.saveOperator(input)).rejects.toThrow('资料保存失败')
    expect(input).toEqual({ ...operator, id: 3 })
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('启停使用专用PUT接口，仅提交目标状态，返回后端账号记录', async () => {
    const saved = { ...operator, id: 3, status: 0, accountRole: 'OPERATOR' }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 1, data: saved })))
    const { api } = await import('./index')
    await expect(api.setOperatorStatus(3, 0)).resolves.toEqual(saved)
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee/3/status', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 0 }) }))
    await api.setOperatorStatus(3, 1)
    expect(fetch).toHaveBeenLastCalledWith('/api/admin/employee/3/status', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 1 }) }))
  })
  it.each([403, 404])('启停HTTP %i错误直接展示，不回退整对象保存或演示数据', async status => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '账号操作被拒绝' }), { status }))
    const { api } = await import('./index')
    await expect(api.setOperatorStatus(3, 0)).rejects.toThrow('账号操作被拒绝')
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('分页传递页码、条数、关键词和停用状态，读取服务端总数', async () => {
    const result = { total: 35, records: [{ ...operator, id: 3, accountRole: 'OPERATOR' }] }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 1, data: result })))
    const { api } = await import('./index')
    await expect(api.operatorsPage({ page: 4, pageSize: 10, name: '分页_%', status: 0 })).resolves.toEqual(result)
    const [url] = vi.mocked(fetch).mock.calls[0]!
    const parsed = new URL(String(url), 'http://localhost')
    expect(parsed.pathname).toBe('/api/admin/employee/page')
    expect(Object.fromEntries(parsed.searchParams)).toEqual({ page: '4', pageSize: '10', name: '分页_%', status: '0' })
  })
  it('空查询使用后端默认值，分页失败不会回退旧列表或演示数据', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '没有账号管理权限' }), { status: 403 }))
    const { api } = await import('./index')
    await expect(api.operatorsPage()).rejects.toThrow('没有账号管理权限')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee/page', expect.any(Object))
  })
  it('新增提交原始 initialPassword，不发送角色、确认密码或额外字段', async () => {
    const { api } = await import('./index')
    await api.saveOperator({ ...operator, username: ' newuser ', name: ' 新运营 ' }, '  password123  ')
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee', expect.objectContaining({ method: 'POST', body: JSON.stringify({ id: 0, username: 'newuser', name: '新运营', phone: '13800000003', status: 1, initialPassword: '  password123  ' }) }))
  })
  it('编辑不提交密码；缺失初始密码时阻止新增请求', async () => {
    const { api } = await import('./index')
    await expect(api.saveOperator(operator)).rejects.toThrow('请输入初始密码')
    expect(fetch).not.toHaveBeenCalled()
    await api.saveOperator({ ...operator, id: 3 }, 'IgnoredPass123!')
    expect(fetch).toHaveBeenCalledWith('/api/admin/employee', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ id: 3, name: '新运营', phone: '13800000003' }) }))
  })
})
