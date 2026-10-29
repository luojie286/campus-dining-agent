import { readFileSync } from 'node:fs'
import { afterEach, expect, it, vi } from 'vitest'

// 显式提供本地测试会话才运行；不把凭证写入源码或演示数据。
const sessionFile = process.env.MENU_INTEGRATION_SESSION
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
it.skipIf(!sessionFile)('经Vite代理联调真实新增、详情、编辑、启停和删除', async () => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', 'http://127.0.0.1:5173/api')
  const session = readFileSync(sessionFile!, 'utf8').trim()
  vi.stubGlobal('localStorage', { getItem: () => session, removeItem: () => undefined })
  vi.stubGlobal('window', new EventTarget())
  const { api } = await import('./index')
  const category = (await api.categories()).find(category => category.type === 1 && category.status === 1)
  expect(category).toBeDefined()
  const name = `前端联调_${Date.now()}`
  const saved = await api.saveMenu({ id: 0, kind: 'dish', name, categoryId: category!.id,
    price: 18.5, description: '前端API临时联调记录', status: 0, stock: 3,
    tags: ['不辣'], art: 'chicken', composition: [] })
  const detail = await api.menuItem('dish', saved.id)
  expect(detail.name).toBe(name)
  const edited = await api.saveMenu({ ...detail, name: `${name}改`, stock: 999 })
  expect(edited.stock).toBe(3)
  expect((await api.menu('dish', { name: `${name}改` })).total).toBe(1)
  const selling = await api.setMenuStatus(edited, 1)
  await expect(api.deleteMenu(selling)).rejects.toThrow('停售')
  const stopped = await api.setMenuStatus(selling, 0)
  await api.deleteMenu(stopped)
  expect((await api.menu('dish', { name })).total).toBe(0)
  await expect(api.menuItem('dish', saved.id)).rejects.toThrow('不存在')
}, 30000)
