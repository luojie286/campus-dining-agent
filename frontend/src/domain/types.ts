export type Role = 'admin' | 'user'
export type ItemKind = 'dish' | 'setmeal'
export type OrderStatus = 'UNPAID' | 'PREPARING' | 'READY_DELIVERY' | 'DELIVERING' | 'READY_PICKUP' | 'COMPLETED' | 'CANCELLED'
export type AccountRole = 'ACCOUNT_ADMIN' | 'OPERATOR'

export interface Session {
  id: number
  name: string
  role: Role
  token: string
  accountRole?: AccountRole
}
export interface Category { id: number; name: string; type: 1 | 2; sort: number; status: 0 | 1 }
export interface CategoryPageQuery { page?: number; pageSize?: number; name?: string; type?: 1 | 2; status?: 0 | 1 }
export interface MenuItem {
  image?: string
  id: number; kind: ItemKind; name: string; categoryId: number; price: number
  description: string; status: 0 | 1; stock: number; tags: string[]
  art: 'chicken' | 'fish' | 'beef' | 'salad' | 'noodle' | 'tofu'
  composition: { dishId: number; quantity: number }[]
}
export interface Operator { id: number; username: string; name: string; phone: string; status: 0 | 1; accountRole?: AccountRole }
export interface Address { id: number; userId: number; consignee: string; phone: string; building: string; detail: string; isDefault: boolean }
export interface CartLine { itemId: number; quantity: number }
export interface OrderLine { itemId: number; name: string; kind: ItemKind; price: number; quantity: number }
export interface OrderEvent { status: OrderStatus; time: string; note: string }
export interface Order {
  paymentChannel?: 'MOCK' | 'WECHAT'
  refundStatus?: 'NONE' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
  refundNo?: string
  id: number; number: string; userId: number; userName: string; status: OrderStatus
  payStatus: 'UNPAID' | 'PAID' | 'REFUNDED'; fulfillment: 'DELIVERY' | 'PICKUP'
  amount: number; createdAt: string; paidAt?: string; paymentDeadline: string
  recipient: string; phone: string; address: string; remark: string; lines: OrderLine[]
  events: OrderEvent[]; reserved?: { itemId: number; quantity: number }[]
}
export interface Shop { name: string; open: boolean; openingHours: string; pickupPoint: string }
export interface Dashboard {
  revenue: number; paidOrders: number; averageAmount: number; completedOrders: number
  cancellationRate: number; orderCount: number; counts: Record<OrderStatus, number>
  trend: { date: string; revenue: number; orders: number }[]
  sales: { id: number; name: string; quantity: number; amount: number }[]
  period: { begin: string; end: string }; metricNote: string
}
export interface Page<T> { total: number; records: T[] }
export interface OperatorPageQuery { page?: number; pageSize?: number; name?: string; status?: 0 | 1 }
export interface ListQuery { name?: string; status?: string | number; categoryId?: number; page?: number; pageSize?: number; begin?: string; end?: string }
export interface ToolTrace { name: string; summary: string; state: 'success' | 'failed' }
export interface PendingAction { id: string; label: string; detail: string; expiresAt: string }
export interface AgentReply { sessionId: string; content: string; items?: MenuItem[]; orders?: Order[]; metrics?: Dashboard; traces: ToolTrace[]; action?: PendingAction }
export interface ChatMessage extends Partial<AgentReply> { id: string; role: 'user' | 'assistant'; content: string; actionState?: 'confirmed' | 'cancelled' }
export const statusLabels: Record<OrderStatus, string> = {
  UNPAID: '待支付', PREPARING: '制作中', READY_DELIVERY: '待配送', DELIVERING: '配送中',
  READY_PICKUP: '待自提', COMPLETED: '已完成', CANCELLED: '已取消'
}
export const statusColors: Record<OrderStatus, string> = {
  UNPAID: 'amber', PREPARING: 'blue', READY_DELIVERY: 'blue', DELIVERING: 'blue',
  READY_PICKUP: 'green', COMPLETED: 'green', CANCELLED: 'gray'
}
export const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export function localDate(value: Date | string = new Date()): string {
  const d = typeof value === 'string' ? new Date(value) : value
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
export function dateTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}
