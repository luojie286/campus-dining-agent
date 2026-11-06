import type { Session } from '@/domain/types'

export const API_MODE: 'mock' | 'api' = import.meta.env.VITE_API_MODE === 'api' ? 'api' : 'mock'
export const SESSION_KEY = `campus-session-${API_MODE}-v1`
const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')

export function productImageUrl(url: string): string {
  if (url.startsWith('/api/media/product-images/')) return base + url.slice('/api'.length)
  return url
}

export function readSession(): Session | null {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY) || 'null') as Session | null
    return session && (session.role === 'admin' || session.role === 'user') && typeof session.token === 'string' ? session : null
  } catch { return null }
}
export function writeSession(session: Session | null): void {
  if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  else localStorage.removeItem(SESSION_KEY)
}

export async function request<T>(path: string, method = 'GET', body?: unknown, query?: Record<string, unknown>): Promise<T> {
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') params.set(k, String(v)) })
  const session = readSession()
  const isMultipart = body instanceof FormData
  const headers: Record<string, string> = {}
  // 浏览器会为 FormData 自动生成带 boundary 的 Content-Type。
  if (!isMultipart) headers['Content-Type'] = 'application/json'
  if (session) headers.token = session.token
  let response: Response
  try {
    response = await fetch(`${base}${path}${params.size ? '?' + params : ''}`, {
      method, headers, body: isMultipart ? body : body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000)
    })
  } catch { throw new Error('无法连接后端，请检查 8080 服务与代理配置。真实接口模式不会回退为演示数据。') }
  if (response.status === 401) {
    writeSession(null)
    window.dispatchEvent(new Event('session-expired'))
    throw new Error('登录已过期，请重新登录')
  }
  if (!response.ok) {
    const failure = await response.json().catch(() => null) as { msg?: unknown } | null
    const message = typeof failure?.msg === 'string' ? failure.msg.trim() : ''
    throw new Error(message || (response.status === 403 ? '当前账号无权执行此操作' : response.status === 404 ? `接口 ${path} 尚未实现（404），请按接口文档编写后端` : `接口请求失败（${response.status}）`))
  }
  const result = await response.json() as { code: number; msg?: string; data: T }
  if (result.code !== 1) throw new Error(result.msg || '业务操作失败')
  return result.data
}
