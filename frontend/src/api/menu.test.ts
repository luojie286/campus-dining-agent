import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const item = { id: 40, kind: 'dish' as const, name: ' 菜品 ', categoryId: 1, price: 18.5,
  description: '', status: 0 as const, stock: 7, tags: ['不辣'], art: 'chicken' as const, composition: [] }
beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', '/api')
  vi.stubGlobal('localStorage', { getItem: () => JSON.stringify({ id: 1, name: '测试运营', role: 'admin', token: 'test-token' }) })
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 1, data: item }))))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('编辑先读详情，返回最新数据', async () => {
  const { api } = await import('./index')
  expect(await api.menuItem('dish', 40)).toEqual(item)
  expect(fetch).toHaveBeenCalledWith('/api/admin/dish/40', expect.objectContaining({ method: 'GET' }))
})
it('编辑不提交库存或额外字段，且不改变表单', async () => {
  const { api } = await import('./index')
  const input = { ...item, createUser: 999 }
  await api.saveMenu(input)
  const body = JSON.parse(vi.mocked(fetch).mock.calls[0]![1]!.body as string)
  expect(body).not.toHaveProperty('stock')
  expect(body).not.toHaveProperty('createUser')
  expect(body.name).toBe('菜品')
  expect(input.name).toBe(' 菜品 ')
})
it('新增提交初始库存', async () => {
  const { api } = await import('./index')
  await api.saveMenu({ ...item, id: 0 })
  expect(fetch).toHaveBeenCalledWith('/api/admin/dish', expect.objectContaining({ method: 'POST' }))
  expect(JSON.parse(vi.mocked(fetch).mock.calls[0]![1]!.body as string).stock).toBe(7)
})
it('启停兼容PUT先获取最新资料，保留最新姓名且不携带旧库存', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 1, data: { ...item, name: '最新姓名', stock: 3 } })))
  const { api } = await import('./index')
  await api.setMenuStatus(item, 1)
  expect(fetch).toHaveBeenCalledTimes(2)
  const body = JSON.parse(vi.mocked(fetch).mock.calls[1]![1]!.body as string)
  expect(body.name).toBe('最新姓名')
  expect(body.status).toBe(1)
  expect(body).not.toHaveProperty('stock')
})
it.each([400, 403, 404, 409])('保存HTTP %s错误直传，不回退演示且不修改输入', async status => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '保存被拒绝' }), { status }))
  const { api } = await import('./index')
  await expect(api.saveMenu(item)).rejects.toThrow('保存被拒绝')
  expect(item.stock).toBe(7)
  expect(fetch).toHaveBeenCalledTimes(1)
})
it('删除调用DELETE并展示后端引用冲突', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '菜品被套餐引用，不能删除' }), { status: 409 }))
  const { api } = await import('./index')
  await expect(api.deleteMenu(item)).rejects.toThrow('菜品被套餐引用')
  expect(fetch).toHaveBeenCalledWith('/api/admin/dish/40', expect.objectContaining({ method: 'DELETE' }))
})
it('分页保留停售筛选、关键词和每页条数', async () => {
  const { api } = await import('./index')
  await api.menu('dish', { page: 2, pageSize: 20, name: '%_!', status: 0, categoryId: 1 })
  const url = new URL(String(vi.mocked(fetch).mock.calls[0]![0]), 'http://localhost')
  expect(url.pathname).toBe('/api/admin/dish/page')
  expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', pageSize: '20', name: '%_!', status: '0', categoryId: '1' })
})
