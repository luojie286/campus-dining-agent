import type { Session } from '@/domain/types'

export const API_MODE: 'mock' | 'api' = import.meta.env.VITE_API_MODE === 'api' ? 'api' : 'mock'
export const SESSION_KEY = `campus-session-${API_MODE}-v1`
const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')

// 携带 HTTP 状态的错误，便于界面针对 429 冷却、409 上限等给出可读提示。
export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly retryAfter: string | null = null) {
    super(message)
    this.name = 'ApiError'
  }
}

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
    const fallback = response.status === 403 ? '当前账号无权执行此操作'
      : response.status === 404 ? `接口 ${path} 尚未实现（404），请按接口文档编写后端`
      : `接口请求失败（${response.status}）`
    throw new ApiError(message || fallback, response.status, response.headers.get('Retry-After'))
  }
  const result = await response.json() as { code: number; msg?: string; data: T }
  if (result.code !== 1) throw new Error(result.msg || '业务操作失败')
  return result.data
}

function parseFilename(disposition: string | null): string {
  if (!disposition) return ''
  // RFC 5987：filename*=UTF-8''percent-encoded，优先于普通 filename。
  const starred = disposition.match(/filename\*\s*=\s*[^']*'[^']*'([^;]+)/i)
  if (starred?.[1]) {
    try { return decodeURIComponent(starred[1].trim().replace(/^"|"$/g, '')) } catch { /* 回退到普通 filename */ }
  }
  const plain = disposition.match(/filename\s*=\s*"?([^";]+)"?/i)
  return plain?.[1]?.trim() || ''
}

/** 下载二进制文件（如导出 Excel）。与 request 共用 base、token 头与 401 处理，但不解析成功响应体。 */
export async function download(path: string, query: Record<string, unknown> | undefined, defaultName: string): Promise<void> {
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') params.set(k, String(v)) })
  const session = readSession()
  const headers: Record<string, string> = {}
  if (session) headers.token = session.token
  let response: Response
  try {
    response = await fetch(`${base}${path}${params.size ? '?' + params : ''}`, {
      method: 'GET', headers, signal: AbortSignal.timeout(30000)
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
    const fallback = response.status === 409 ? '导出数据超出限制，请缩短日期范围后重试' : `导出失败（${response.status}）`
    throw new ApiError(message || fallback, response.status, response.headers.get('Retry-After'))
  }
  const blob = await response.blob()
  const filename = parseFilename(response.headers.get('Content-Disposition')) || defaultName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 稍后释放，避免部分浏览器在下载开始前就失效。
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
