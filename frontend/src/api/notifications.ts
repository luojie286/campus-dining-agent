import { reactive } from 'vue'
import { API_MODE, request } from './http'
import { app, toast } from '@/stores/app'
import type { Session, WebSocketOrderMessage, WsTicket } from '@/domain/types'

let socket: WebSocket | null = null
let retry: ReturnType<typeof setTimeout> | undefined
let generation = 0
let delay = 2000

// 断线重连可能重复推送同一事件，按 eventId 去重；限制集合大小避免长时间运行无限增长。
const seenEventIds = new Set<string>()
const SEEN_EVENT_LIMIT = 500

function wsUrl(): URL {
  const configured = import.meta.env.VITE_WS_URL || ''
  const fallback = 'ws://127.0.0.1:8080/ws/orders'
  if (!configured) return new URL(fallback)
  // 绝对 ws/wss 地址可直接使用；相对路径（如 /ws/orders）必须按当前页面推导，否则 new URL 会抛错。
  if (/^wss?:\/\//i.test(configured)) return new URL(configured)
  const page = window.location
  const protocol = page.protocol === 'https:' ? 'wss:' : 'ws:'
  return new URL(configured, `${protocol}//${page.host}`)
}

function rememberEvent(id: string): boolean {
  if (seenEventIds.has(id)) return false
  seenEventIds.add(id)
  if (seenEventIds.size > SEEN_EVENT_LIMIT) {
    const oldest = seenEventIds.values().next().value
    if (oldest !== undefined) seenEventIds.delete(oldest)
  }
  return true
}

export const notifications = reactive({
  connected: false, unread: 0,
  connect(session: Session | null) {
    const current = ++generation
    clearTimeout(retry); socket?.close(); socket = null; notifications.connected = false; notifications.unread = 0
    if (API_MODE === 'mock' || !session) return
    delay = 2000
    async function open() {
      try {
        // 浏览器握手无法自定义 token header：使用后端签发的一次性短期票据。
        const { ticket } = await request<WsTicket>('/auth/ws-ticket', 'POST', {})
        if (generation !== current) return
        const url = wsUrl(); url.searchParams.set('ticket', ticket)
        const connection = new WebSocket(url)
        socket = connection
        connection.onopen = () => {
          if (generation !== current || socket !== connection) return
          notifications.connected = true; delay = 2000
        }
        connection.onmessage = event => {
          if (generation !== current || socket !== connection) return
          try {
            const payload = JSON.parse(String(event.data)) as WebSocketOrderMessage
            if (!payload || typeof payload !== 'object' || !payload.type) return
            // 兼容旧服务端缺少 eventId 的情况：用关键字段拼一个稳定的去重键。
            const eventId = payload.eventId || `${payload.type}-${payload.orderId}-${payload.occurredAt || ''}`
            if (!rememberEvent(eventId)) return
            if (payload.type === 'ORDER_URGED') {
              // 催单不改履约状态，仅给运营提示。
              notifications.unread++
              toast(`催单提醒：${payload.orderNumber || '订单'}（第 ${payload.urgeCount ?? 1} 次）`, 'info')
              return
            }
            if (payload.type === 'NEW_ORDER' || payload.type === 'ORDER_STATUS_CHANGED') {
              notifications.unread++; app.revision++; toast(`订单动态：${payload.orderNumber || '订单状态已更新'}`, 'info')
            }
          } catch { /* 忽略非协议消息，权限过滤必须在后端完成。 */ }
        }
        connection.onclose = () => {
          if (generation !== current || socket !== connection) return
          socket = null
          notifications.connected = false
          schedule()
        }
        connection.onerror = () => { connection.close() }
      } catch { schedule() }
    }
    function schedule() {
      if (generation !== current) return
      retry = setTimeout(open, delay); delay = Math.min(delay * 2, 30000)
    }
    void open()
  }
})
