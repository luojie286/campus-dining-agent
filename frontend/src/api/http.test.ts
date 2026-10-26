import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readSession, request, writeSession } from './http'
import type { Session } from '@/domain/types'

const session: Session = { id: 1, name: '管理员', role: 'admin', token: 'test-token', accountRole: 'ACCOUNT_ADMIN' }

beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key)
  })
  vi.stubGlobal('window', new EventTarget())
  writeSession(session)
})
afterEach(() => vi.unstubAllGlobals())

describe('真实接口权限错误', () => {
  it('403 展示后端消息并保留会话，供用户继续使用普通运营功能', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: '当前账号没有运营账号管理权限', data: null }), { status: 403 })))
    await expect(request('/admin/employee/list')).rejects.toThrow('当前账号没有运营账号管理权限')
    expect(readSession()).toEqual(session)
  })

  it('非 JSON 的 403 使用权限提示，401 仍清除登录并通知页面', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response('<html>Forbidden</html>', { status: 403 })).mockResolvedValueOnce(new Response('', { status: 401 })))
    await expect(request('/admin/employee/list')).rejects.toThrow('当前账号无权执行此操作')
    const expired = vi.fn()
    window.addEventListener('session-expired', expired)
    await expect(request('/admin/employee/list')).rejects.toThrow('登录已过期')
    expect(readSession()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })
})
