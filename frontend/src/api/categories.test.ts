import { afterEach, beforeEach, expect, it, vi } from 'vitest'

beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', '/api')
  vi.stubGlobal('localStorage', { getItem: () => null })
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response(JSON.stringify({ code: 1, data: { total: 0, records: [] } }))))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('分类分页传递筛选条件，启停仅提交状态，旧下拉列表保持数组接口', async () => {
  const { api } = await import('./index')
  await api.categoriesPage({ page: 2, pageSize: 10, name: '热_%', type: 1, status: 0 })
  const url = new URL(String(vi.mocked(fetch).mock.calls[0]![0]), 'http://localhost')
  expect(url.pathname).toBe('/api/admin/category/page')
  expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', pageSize: '10', name: '热_%', type: '1', status: '0' })
  await api.setCategoryStatus(3, 0)
  expect(fetch).toHaveBeenLastCalledWith('/api/admin/category/3/status', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 0 }) }))
  await api.categories()
  expect(fetch).toHaveBeenLastCalledWith('/api/user/category/list', expect.any(Object))
})

it('分类操作被后端拒绝时直接报错，不回退整对象保存或演示模式', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '分类已关联菜品或套餐，不能删除' }), { status: 409 }))
  const { api } = await import('./index')
  await expect(api.deleteCategory(3)).rejects.toThrow('不能删除')
  expect(fetch).toHaveBeenCalledTimes(1)
})
