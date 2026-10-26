import { reactive } from 'vue'
import { API_MODE, request } from './http'
import { app, toast } from '@/stores/app'
import type { Session } from '@/domain/types'

let socket: WebSocket | null = null
let retry: ReturnType<typeof setTimeout> | undefined
let generation = 0
let delay = 2000
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
        const { ticket } = await request<{ ticket: string }>('/auth/ws-ticket', 'POST', {})
        if (generation !== current) return
        const url = new URL(import.meta.env.VITE_WS_URL || 'ws://127.0.0.1:8080/ws/orders'); url.searchParams.set('ticket', ticket)
        socket = new WebSocket(url)
        socket.onopen = () => { notifications.connected = true; delay = 2000 }
        socket.onmessage = event => {
          try {
            const payload = JSON.parse(String(event.data)) as { type: string; orderNumber?: string }
            if (['NEW_ORDER', 'ORDER_STATUS_CHANGED'].includes(payload.type)) {
              notifications.unread++; app.revision++; toast(`订单动态：${payload.orderNumber || '订单状态已更新'}`, 'info')
            }
          } catch { /* 忽略非协议消息，权限过滤必须在后端完成。 */ }
        }
        socket.onclose = () => { notifications.connected = false; schedule() }
        socket.onerror = () => { socket?.close() }
      } catch { schedule() }
    }
    function schedule() {
      if (generation !== current) return
      retry = setTimeout(open, delay); delay = Math.min(delay * 2, 30000)
    }
    void open()
  }
})
