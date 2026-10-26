import { computed, reactive } from 'vue'
import { api } from '@/api'
import { API_MODE, readSession, writeSession } from '@/api/http'
import type { CartLine, Category, MenuItem, Session, Shop } from '@/domain/types'

export const app = reactive({
  session: readSession() as Session | null,
  shop: null as Shop | null, categories: [] as Category[], menu: [] as MenuItem[], cart: [] as CartLine[],
  toasts: [] as { id: number; message: string; type: 'success' | 'error' | 'info' }[],
  revision: 0, sessionRevision: 0, loading: false
})
export const cartCount = computed(() => app.cart.reduce((sum, x) => sum + x.quantity, 0))
export const cartAmount = computed(() => app.cart.reduce((sum, line) => sum + (app.menu.find(x => x.id === line.itemId)?.price || 0) * line.quantity, 0))
let toastId = 0
export function toast(message: string, type: 'success' | 'error' | 'info' = 'success'): void {
  const id = ++toastId; app.toasts.push({ id, message, type })
  setTimeout(() => { app.toasts = app.toasts.filter(x => x.id !== id) }, 5000)
}
export function setSession(session: Session | null): void {
  app.session = session; writeSession(session); app.sessionRevision++
  app.menu = []; app.cart = []; app.categories = []; app.shop = null
}
export async function refreshBase(): Promise<void> {
  const owner = app.session
  if (!owner) return
  async function catalog() {
    const first = await api.menu(undefined, { page: 1, pageSize: 100 })
    if (!Number.isInteger(first.total) || first.total < 0 || first.total > 10000) throw new Error('菜单数量协议异常或超过前端目录上限，请检查分页接口')
    const records = [...first.records]
    for (let page = 2; page <= Math.ceil(first.total / 100); page++) records.push(...(await api.menu(undefined, { page, pageSize: 100 })).records)
    return [...new Map(records.map(item => [item.id, item])).values()]
  }
  const [shop, categories, menu, cart] = await Promise.all([api.shop(), api.categories(), catalog(), owner.role === 'user' ? api.cart() : Promise.resolve([])])
  if (owner !== app.session) return
  app.shop = shop; app.categories = categories; app.menu = menu; app.cart = cart
  app.revision++
}
// 分类维护只同步分类缓存，不依赖店铺、商品或购物车接口。
export async function refreshCategories(): Promise<void> {
  const owner = app.session
  if (!owner) return
  const categories = await api.categories()
  if (owner !== app.session) return
  app.categories = categories
  app.revision++
}
let cartQueue: Promise<void> = Promise.resolve()
export function changeCart(itemId: number, delta: number): Promise<void> {
  const owner = app.session
  const operation = cartQueue.catch(() => undefined).then(async () => {
    if (!owner || owner !== app.session) throw new Error('身份已变化，请重新操作购物车')
    const lines = app.cart.map(x => ({ ...x })); const line = lines.find(x => x.itemId === itemId)
    if (line) line.quantity += delta; else if (delta > 0) lines.push({ itemId, quantity: delta })
    app.cart = await api.setCart(lines.filter(x => x.quantity > 0))
  })
  cartQueue = operation
  return operation
}
export async function safely(action: () => Promise<void>, message?: string): Promise<boolean> {
  try { await action(); if (message) toast(message); return true }
  catch (error) { toast(error instanceof Error ? error.message : '操作失败', 'error'); return false }
}

window.addEventListener('session-expired', () => { setSession(null) })
window.addEventListener('demo-order-updated', () => { app.revision++ })
window.addEventListener('storage', event => {
  if (event.key === `campus-session-${API_MODE}-v1`) { app.session = readSession(); app.sessionRevision++ }
})
