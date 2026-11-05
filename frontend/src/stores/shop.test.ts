import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const shop = {
  name: '园区食堂',
  open: false,
  openingHours: '11:00–13:00',
  pickupPoint: '一楼自提点'
}

function success(data: unknown): Response {
  return new Response(JSON.stringify({ code: 1, data }))
}

beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', '/api')
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('localStorage', {
    getItem: () => JSON.stringify({ role: 'admin', token: 'test-token' })
  })
  vi.stubGlobal('fetch', vi.fn())
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

it('目录查询失败时仍显示成功读取的营业状态', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(success(shop))
    .mockResolvedValueOnce(success([]))
    .mockResolvedValueOnce(new Response(JSON.stringify({ msg: '目录暂不可用' }), { status: 503 }))

  const { app, refreshBase } = await import('./app')
  await expect(refreshBase()).rejects.toThrow('目录暂不可用')

  expect(app.shop).toEqual(shop)
  expect(app.shopError).toBe('')
})

it('读取失败显示未知，重试和设置成功后采用服务端返回值', async () => {
  const reopenedShop = { ...shop, open: true }
  vi.mocked(fetch).mockResolvedValueOnce(
    new Response(JSON.stringify({ msg: '店铺暂不可用' }), { status: 503 })
  ).mockResolvedValueOnce(success(shop))
    .mockResolvedValueOnce(success(reopenedShop))

  const { app, refreshShop, updateShopStatus } = await import('./app')
  await refreshShop()
  expect(app.shop).toBeNull()
  expect(app.shopError).toBe('店铺暂不可用')

  await refreshShop()
  expect(app.shop?.open).toBe(false)
  expect(app.shopError).toBe('')

  await updateShopStatus(true)
  expect(app.shop).toEqual(reopenedShop)
  expect(fetch).toHaveBeenLastCalledWith('/api/admin/shop', expect.objectContaining({
    method: 'PUT',
    body: '{"open":true}'
  }))
})
