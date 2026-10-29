import { createSeed, type DemoData } from './seed'
import { availableQuantity, canTransition, inventoryRequirements } from '@/domain/rules'
import { localDate, statusLabels, type Address, type AgentReply, type CartLine, type Category, type Dashboard, type ListQuery, type MenuItem, type Operator, type Order, type OrderStatus, type Page, type Role, type Session, type Shop } from '@/domain/types'
import { readSession } from './http'
import { canManageOperators } from '@/domain/permissions'
import { initialPasswordError } from '@/domain/operatorPassword'
import type { CategoryPageQuery } from '@/domain/types'
import type { OperatorPageQuery } from '@/domain/types'

const DATA_KEY = 'campus-demo-v1'
let data: DemoData
function load(): DemoData {
  try {
    const saved = JSON.parse(localStorage.getItem(DATA_KEY) || 'null') as DemoData | null
    if (saved?.version === 1 && Array.isArray(saved.menu) && Array.isArray(saved.orders) && Array.isArray(saved.operators)) {
      // 兼容旧演示数据，只给初始 admin 账号补上管理员角色，不重置业务数据。
      saved.operators = saved.operators.map(operator => ({
        ...operator,
        accountRole: operator.accountRole ?? (operator.id === 1 && operator.username === 'admin' ? 'ACCOUNT_ADMIN' : 'OPERATOR')
      }))
      return saved
    }
  } catch { /* damaged local data: regenerate demo */ }
  return createSeed()
}
function database(): DemoData { return data ||= load() }
function persist(): void { localStorage.setItem(DATA_KEY, JSON.stringify(database())) }
function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
function requireSession(role?: Role): Session {
  const session = readSession()
  if (!session || (role && session.role !== role)) throw new Error('当前身份无权执行此操作')
  return session
}
function requireAccountManagement(): void {
  const session = requireSession('admin')
  const operator = database().operators.find(x => x.id === session.id && x.status === 1)
  if (!operator || !canManageOperators({ ...session, accountRole: operator.accountRole })) throw new Error('当前账号没有运营账号管理权限')
}
function requireOperationsAccess(): void {
  const session = requireSession('admin')
  const operator = database().operators.find(item => item.id === session.id)
  if (!operator || operator.status !== 1 || !['ACCOUNT_ADMIN', 'OPERATOR'].includes(operator.accountRole ?? '')) {
    throw new Error('当前账号没有餐饮运营权限')
  }
}
function nextId(list: { id: number }[]): number { return Math.max(0, ...list.map(x => x.id)) + 1 }
// 仅用于浏览器演示；真实认证与 BCrypt 存储由后端负责。
async function demoPasswordHash(password: string, salt: number[]): Promise<string> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: new Uint8Array(salt), iterations: 100000 }, key, 256)
  return Array.from(new Uint8Array(bits), byte => byte.toString(16).padStart(2, '0')).join('')
}
function page<T>(items: T[], query: ListQuery): Page<T> {
  const pageNo = Math.max(1, Number(query.page || 1)); const size = Math.min(100, Math.max(1, Number(query.pageSize || 12)))
  return clone({ total: items.length, records: items.slice((pageNo - 1) * size, pageNo * size) })
}
function notifyOrder(): void { window.dispatchEvent(new Event('demo-order-updated')) }
function release(order: Order): void {
  for (const line of order.reserved || []) {
    const item = database().menu.find(x => x.id === line.itemId)
    if (item) item.stock += line.quantity
  }
  order.reserved = []
}
function expireOrders(): void {
  let changed = false
  database().orders.forEach(order => {
    if (order.status === 'UNPAID' && new Date(order.paymentDeadline).getTime() <= Date.now()) {
      order.status = 'CANCELLED'; release(order)
      order.events.push({ status: 'CANCELLED', time: new Date().toISOString(), note: '前端演示：支付超时关闭' }); changed = true
    }
  })
  if (changed) { persist(); notifyOrder() }
}
function ownedOrder(id: number): Order {
  const session = requireSession(); expireOrders()
  const order = database().orders.find(x => x.id === id)
  if (!order || (session.role === 'user' && order.userId !== session.id)) throw new Error('订单不存在或无权访问')
  return order
}
function validateMenu(item: MenuItem): void {
  if (!item.name.trim()) throw new Error('商品名称不能为空')
  if (!Number.isFinite(item.price) || item.price <= 0 || item.price > 9999) throw new Error('价格必须大于 0 且不超过 9999 元')
  if (!Number.isInteger(item.stock) || item.stock < 0) throw new Error('库存必须为非负整数')
  const category = database().categories.find(x => x.id === item.categoryId)
  if (!category || category.type !== (item.kind === 'dish' ? 1 : 2)) throw new Error('请选择匹配的商品分类')
  if (item.kind === 'setmeal') {
    if (!item.composition.length) throw new Error('套餐至少需要一个组成菜品')
    if (new Set(item.composition.map(x => x.dishId)).size !== item.composition.length) throw new Error('套餐组成菜品不能重复')
    item.composition.forEach(part => {
      if (!database().menu.some(x => x.id === part.dishId && x.kind === 'dish') || !Number.isInteger(part.quantity) || part.quantity < 1) throw new Error('套餐组成或数量不合法')
    })
  }
}
function dates(begin?: string, end?: string): { begin: string; end: string } {
  const today = new Date(); const first = new Date(today); first.setDate(first.getDate() - 6)
  const b = begin || localDate(first); const e = end || localDate(today)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(b) || !/^\d{4}-\d{2}-\d{2}$/.test(e) || b > e || (new Date(e).getTime() - new Date(b).getTime()) / 86400000 > 90) throw new Error('日期范围必须有效，且不超过 90 天')
  return { begin: b, end: e }
}

interface ActionRecord { owner: number; role: Role; kind: 'cancel' | 'sale'; targetId: number; previous: string | number; expiresAt: number; executed: boolean; cancelled: boolean }
const actions = new Map<string, ActionRecord>()

export const mock = {
  async login(role: Role, username: string, password: string): Promise<Session> {
    if (role === 'admin') {
      const operator = database().operators.find(x => x.username === username && x.status === 1)
      if (operator) {
        const credential = database().operatorCredentials?.[operator.id]
        const matches = credential ? await demoPasswordHash(password, credential.salt) === credential.hash : password === 'admin123'
        if (matches) return { id: operator.id, name: operator.name, role, accountRole: operator.accountRole, token: `demo-admin-${operator.id}` }
      }
    } else if (username === 'staff' && password === '123456') return { id: 1001, name: '林同学', role, token: 'demo-user-1001' }
    throw new Error('演示账号或密码不正确，或账号已停用')
  },
  async shop(): Promise<Shop> { requireSession(); return clone(database().shop) },
  async setShop(open: boolean): Promise<Shop> { requireSession('admin'); database().shop.open = open; persist(); return clone(database().shop) },
  async menu(kind?: 'dish' | 'setmeal', query: ListQuery = {}): Promise<Page<MenuItem>> {
    const session = requireSession(); const db = database()
    let items = db.menu.filter(x => !kind || x.kind === kind)
    if (session.role === 'user') items = items.filter(x => x.status === 1 && db.categories.some(c => c.id === x.categoryId && c.status === 1))
    if (query.name) {
      const keyword = query.name.trim()
      items = items.filter(x => x.name.includes(keyword) || (session.role !== 'admin' && x.tags.some(t => t.includes(keyword))))
    }
    if (query.categoryId) items = items.filter(x => x.categoryId === query.categoryId)
    if (query.status !== undefined && query.status !== '') items = items.filter(x => x.status === Number(query.status))
    items.sort((left, right) => {
      const a = db.categories.find(category => category.id === left.categoryId)
      const b = db.categories.find(category => category.id === right.categoryId)
      return (a?.sort ?? 0) - (b?.sort ?? 0) || left.categoryId - right.categoryId || left.id - right.id
    })
    return page(items, query)
  },
  async menuItem(kind: MenuItem['kind'], id: number): Promise<MenuItem> {
    requireOperationsAccess()
    const item = database().menu.find(item => item.id === id && item.kind === kind)
    if (!item) throw new Error('商品不存在')
    return clone(item)
  },
  async saveMenu(item: MenuItem): Promise<MenuItem> {
    requireSession('admin'); validateMenu(item)
    const list = database().menu; const index = list.findIndex(x => x.id === item.id)
    const saved = { ...clone(item), id: index >= 0 ? item.id : nextId(list) }
    if (index >= 0) saved.stock = list[index]!.stock
    if (item.status === 1 && !database().categories.find(category => category.id === item.categoryId)?.status) throw new Error('所属分类已禁用，不能起售')
    if (index >= 0) list[index] = saved; else list.push(saved)
    persist(); return clone(saved)
  },
  async deleteMenu(id: number): Promise<void> {
    requireSession('admin'); const db = database()
    if (db.menu.some(x => x.kind === 'setmeal' && x.composition.some(p => p.dishId === id))) throw new Error('该菜品被套餐引用，请先修改套餐组成')
    if (db.orders.some(o => !['COMPLETED', 'CANCELLED'].includes(o.status) && o.lines.some(l => l.itemId === id))) throw new Error('该商品关联未完成订单，请先停售，完成订单后再删除')
    const target = db.menu.find(x => x.id === id)
    if (!target) return
    if (target.status === 1) throw new Error('请先停售，再删除商品')
    db.menu = db.menu.filter(x => x.id !== id); persist()
  },
  async categories(): Promise<Category[]> {
    const session = requireSession()
    if (session.role === 'admin') requireOperationsAccess()
    return clone(database().categories.filter(x => session.role === 'admin' || x.status === 1).sort((a, b) => a.sort - b.sort || a.id - b.id))
  },
  async categoriesPage(query: CategoryPageQuery = {}): Promise<Page<Category>> {
    requireOperationsAccess()
    const pageNo = query.page ?? 1
    const pageSize = query.pageSize ?? 8
    const name = (query.name ?? '').trim().toLowerCase()
    if (!Number.isInteger(pageNo) || pageNo < 1 || pageNo > 1000000 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || name.length > 100) throw new Error('分页查询参数不合法')
    if (query.type !== undefined && query.type !== 1 && query.type !== 2) throw new Error('分类类型只能为1或2')
    if (query.status !== undefined && query.status !== 0 && query.status !== 1) throw new Error('分类状态只能为0或1')
    const records = database().categories
      .filter(item => !name || item.name.toLowerCase().includes(name))
      .filter(item => query.type === undefined || item.type === query.type)
      .filter(item => query.status === undefined || item.status === query.status)
      .sort((a, b) => a.sort - b.sort || a.id - b.id)
    return page(records, { page: pageNo, pageSize })
  },
  async setCategoryStatus(id: number, status: Category['status']): Promise<Category> {
    requireOperationsAccess()
    if (status !== 0 && status !== 1) throw new Error('分类状态只能为0或1')
    const category = database().categories.find(item => item.id === id)
    if (!category) throw new Error('分类不存在')
    category.status = status
    persist()
    return clone(category)
  },
  async saveCategory(category: Category): Promise<Category> {
    requireOperationsAccess()
    const name = category.name.trim()
    if (!name || name.length > 30) throw new Error('分类名称必须为1到30个字符')
    if (!Number.isInteger(category.sort) || category.sort < 0 || category.sort > 2147483647) throw new Error('排序值必须为0到2147483647的整数')
    if (category.type !== 1 && category.type !== 2) throw new Error('分类类型只能为1或2')
    if (category.status !== 0 && category.status !== 1) throw new Error('分类状态只能为0或1')
    const db = database(); const index = db.categories.findIndex(x => x.id === category.id)
    if (category.id !== 0 && index < 0) throw new Error('分类不存在')
    if (db.categories.some(item => item.id !== category.id && item.name.toLowerCase() === name.toLowerCase())) throw new Error('分类名称已存在')
    if (index >= 0 && db.categories[index]!.type !== category.type && db.menu.some(x => x.categoryId === category.id)) throw new Error('分类已有商品，不能改变分类类型')
    const saved = { ...clone(category), name, id: index >= 0 ? category.id : nextId(db.categories) }
    if (index >= 0) db.categories[index] = saved; else db.categories.push(saved)
    persist(); return clone(saved)
  },
  async deleteCategory(id: number): Promise<void> {
    requireOperationsAccess()
    if (!database().categories.some(item => item.id === id)) throw new Error('分类不存在')
    if (database().menu.some(x => x.categoryId === id)) throw new Error('分类关联商品，不能删除')
    database().categories = database().categories.filter(x => x.id !== id); persist()
  },
  async operators(): Promise<Operator[]> { requireAccountManagement(); return clone(database().operators.filter(x => x.accountRole === 'OPERATOR').sort((a, b) => b.id - a.id)) },
  async operator(id: number): Promise<Operator> {
    requireAccountManagement()
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('运营账号ID不合法')
    const target = database().operators.find(operator => operator.id === id)
    if (!target) throw new Error('运营账号不存在')
    if (target.accountRole !== 'OPERATOR') throw new Error('只能查询和编辑普通运营账号')
    return clone(target)
  },
  async updateOperator(input: Pick<Operator, 'id' | 'name' | 'phone'>): Promise<Operator> {
    requireAccountManagement()
    if (!Number.isSafeInteger(input.id) || input.id <= 0) throw new Error('运营账号ID不合法')
    const target = database().operators.find(operator => operator.id === input.id)
    if (!target) throw new Error('运营账号不存在')
    if (target.accountRole !== 'OPERATOR') throw new Error('只能查询和编辑普通运营账号')

    const name = input.name.trim()
    if (!name || name.length > 30) throw new Error('姓名必须为1到30个字符')
    if (!/^1\d{10}$/.test(input.phone)) throw new Error('手机号必须为11位大陆手机号')

    // 仅更新资料，保留数据库中的当前状态、角色和登录凭证。
    target.name = name
    target.phone = input.phone
    persist()
    return clone(target)
  },
  async operatorsPage(query: OperatorPageQuery = {}): Promise<Page<Operator>> {
    requireAccountManagement()
    const pageNo = query.page ?? 1; const pageSize = query.pageSize ?? 8
    const name = (query.name ?? '').trim().toLowerCase()
    if (!Number.isInteger(pageNo) || pageNo < 1 || pageNo > 1000000 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || name.length > 100 || (query.status !== undefined && query.status !== 0 && query.status !== 1)) throw new Error('分页查询参数不合法')
    const records = database().operators
      .filter(x => x.accountRole === 'OPERATOR')
      .filter(x => query.status === undefined || x.status === query.status)
      .filter(x => !name || x.name.toLowerCase().includes(name) || x.username.toLowerCase().includes(name))
      .sort((a, b) => b.id - a.id)
    return page(records, { page: pageNo, pageSize })
  },
  async setOperatorStatus(id: number, status: Operator['status']): Promise<Operator> {
    requireAccountManagement()
    if (!Number.isSafeInteger(id) || id <= 0 || (status !== 0 && status !== 1)) throw new Error('账号ID或状态不合法')
    const target = database().operators.find(x => x.id === id)
    if (!target) throw new Error('运营账号不存在')
    if (target.accountRole !== 'OPERATOR') throw new Error('只能启用或停用普通运营账号')
    if (target.status !== status) { target.status = status; persist() }
    return clone(target)
  },
  async saveOperator(operator: Operator, initialPassword?: string): Promise<Operator> {
    requireAccountManagement(); const db = database()
    const target = operator.id ? db.operators.find(x => x.id === operator.id) : undefined
    if (operator.id && !target) throw new Error('运营账号不存在')
    if (target && target.accountRole !== 'OPERATOR') throw new Error('此入口只能管理普通运营账号')
    if (!operator.username.trim() || !operator.name.trim() || !/^1\d{10}$/.test(operator.phone)) throw new Error('请填写账号、姓名和有效的 11 位手机号')
    if (db.operators.some(x => x.id !== operator.id && x.username === operator.username)) throw new Error('账号已存在')
    let credential: { salt: number[]; hash: string } | undefined
    if (!operator.id) {
      const password = initialPassword ?? ''
      const error = initialPasswordError(password)
      if (error) throw new Error(error)
      const salt = Array.from(crypto.getRandomValues(new Uint8Array(16)))
      credential = { salt, hash: await demoPasswordHash(password, salt) }
      if (db.operators.some(x => x.username === operator.username)) throw new Error('账号已存在')
    }
    const index = db.operators.findIndex(x => x.id === operator.id)
    const { username, name, phone, status } = operator
    const saved: Operator = { id: index >= 0 ? operator.id : nextId(db.operators), username, name, phone, status, accountRole: 'OPERATOR' }
    if (credential) (db.operatorCredentials ??= {})[saved.id] = credential
    if (index >= 0) db.operators[index] = saved; else db.operators.push(saved)
    persist(); return clone(saved)
  },
  async addresses(): Promise<Address[]> { const s = requireSession('user'); return clone(database().addresses.filter(x => x.userId === s.id)) },
  async saveAddress(address: Address): Promise<Address> {
    const s = requireSession('user'); const db = database()
    if (!address.consignee.trim() || !address.building.trim() || !address.detail.trim() || !/^1\d{10}$/.test(address.phone)) throw new Error('请完整填写收餐人、手机号和配送地址')
    if (address.id && !db.addresses.some(x => x.id === address.id && x.userId === s.id)) throw new Error('地址不存在或无权修改')
    if (address.isDefault) db.addresses.filter(x => x.userId === s.id).forEach(x => { x.isDefault = false })
    const index = db.addresses.findIndex(x => x.id === address.id)
    const saved = { ...clone(address), userId: s.id, id: index >= 0 ? address.id : nextId(db.addresses) }
    if (index >= 0) db.addresses[index] = saved; else db.addresses.push(saved)
    persist(); return clone(saved)
  },
  async deleteAddress(id: number): Promise<void> {
    const s = requireSession('user'); database().addresses = database().addresses.filter(x => !(x.id === id && x.userId === s.id)); persist()
  },
  async cart(): Promise<CartLine[]> { const s = requireSession('user'); return clone(database().carts[s.id] || []) },
  async setCart(lines: CartLine[]): Promise<CartLine[]> {
    const s = requireSession('user')
    if (new Set(lines.map(x => x.itemId)).size !== lines.length) throw new Error('购物车商品不能重复')
    if (lines.length) inventoryRequirements(lines, database().menu)
    database().carts[s.id] = clone(lines); persist(); return clone(lines)
  },
  async submitOrder(input: { fulfillment: 'DELIVERY' | 'PICKUP'; addressId?: number; remark: string }): Promise<Order> {
    const s = requireSession('user'); const db = database()
    if (!db.shop.open) throw new Error('食堂已休息，暂不能下单')
    const cart = db.carts[s.id] || []; const required = inventoryRequirements(cart, db.menu)
    const address = input.fulfillment === 'DELIVERY' ? db.addresses.find(x => x.id === input.addressId && x.userId === s.id) : undefined
    if (input.fulfillment === 'DELIVERY' && !address) throw new Error('请选择本人的收餐地址')
    const now = new Date(); const lines = cart.map(c => { const i = db.menu.find(x => x.id === c.itemId)!; return { itemId: i.id, name: i.name, kind: i.kind, price: i.price, quantity: c.quantity } })
    const order: Order = {
      id: nextId(db.orders), number: `YW${Date.now()}`, userId: s.id, userName: s.name, status: 'UNPAID', payStatus: 'UNPAID',
      fulfillment: input.fulfillment, amount: Math.round(lines.reduce((sum, x) => sum + x.price * x.quantity, 0) * 100) / 100,
      createdAt: now.toISOString(), paymentDeadline: new Date(now.getTime() + 15 * 60000).toISOString(),
      recipient: address?.consignee || s.name, phone: address?.phone || '', address: address ? `${address.building} · ${address.detail}` : db.shop.pickupPoint,
      remark: input.remark.slice(0, 200), lines, reserved: [...required].map(([itemId, quantity]) => ({ itemId, quantity })),
      events: [{ status: 'UNPAID', time: now.toISOString(), note: '前端演示：订单创建，预占库存' }]
    }
    required.forEach((n, id) => { db.menu.find(x => x.id === id)!.stock -= n })
    db.orders.unshift(order); db.carts[s.id] = []; persist(); notifyOrder(); return clone(order)
  },
  async orders(query: ListQuery = {}): Promise<Page<Order>> {
    const s = requireSession(); expireOrders()
    let list = database().orders.filter(x => s.role === 'admin' || x.userId === s.id)
    if (query.status) list = list.filter(x => x.status === query.status)
    if (query.name) list = list.filter(x => x.number.includes(query.name!) || x.userName.includes(query.name!))
    if (query.begin) list = list.filter(x => localDate(x.createdAt) >= query.begin!)
    if (query.end) list = list.filter(x => localDate(x.createdAt) <= query.end!)
    return page(list.sort((a, b) => b.createdAt.localeCompare(a.createdAt)), query)
  },
  async order(id: number): Promise<Order> { return clone(ownedOrder(id)) },
  async orderAction(id: number, action: 'pay' | 'cancel' | 'ready' | 'deliver' | 'complete'): Promise<Order> {
    const s = requireSession(); const order = ownedOrder(id)
    let target: OrderStatus
    if (action === 'pay') {
      if (s.role !== 'user') throw new Error('支付操作仅供订单本人使用')
      if (order.payStatus === 'PAID') return clone(order)
      target = 'PREPARING'
    } else if (action === 'cancel') {
      if (s.role === 'user' && order.status !== 'UNPAID') throw new Error('员工仅能取消待支付订单')
      target = 'CANCELLED'
    } else {
      requireSession('admin')
      target = action === 'ready' ? (order.fulfillment === 'PICKUP' ? 'READY_PICKUP' : 'READY_DELIVERY') : action === 'deliver' ? 'DELIVERING' : 'COMPLETED'
    }
    if (!canTransition(order.status, target)) throw new Error(`当前${statusLabels[order.status]}状态不允许此操作`)
    order.status = target
    if (action === 'pay') { order.payStatus = 'PAID'; order.paidAt = new Date().toISOString() }
    if (action === 'cancel') { release(order); if (order.payStatus === 'PAID') order.payStatus = 'REFUNDED' }
    order.events.push({ status: target, time: new Date().toISOString(), note: `前端演示：${action === 'pay' ? '模拟支付成功' : statusLabels[target]}` })
    persist(); notifyOrder(); return clone(order)
  },
  async metrics(begin?: string, end?: string): Promise<Dashboard> {
    requireSession('admin'); expireOrders(); const period = dates(begin, end)
    const all = database().orders
    const inPeriod = (time: string) => localDate(time) >= period.begin && localDate(time) <= period.end
    const created = all.filter(x => inPeriod(x.createdAt))
    const paid = all.filter(x => x.payStatus === 'PAID' && x.paidAt && inPeriod(x.paidAt))
    const counts = Object.fromEntries(Object.keys(statusLabels).map(k => [k, created.filter(x => x.status === k).length])) as Dashboard['counts']
    const revenue = paid.reduce((sum, x) => sum + x.amount, 0)
    const trend: Dashboard['trend'] = []
    for (let day = new Date(`${period.begin}T12:00:00`); localDate(day) <= period.end; day.setDate(day.getDate() + 1)) {
      const date = localDate(day)
      trend.push({ date, revenue: paid.filter(x => localDate(x.paidAt!) === date).reduce((sum, x) => sum + x.amount, 0), orders: created.filter(x => localDate(x.createdAt) === date).length })
    }
    const sales = new Map<number, Dashboard['sales'][number]>()
    paid.forEach(o => o.lines.forEach(l => { const entry = sales.get(l.itemId) || { id: l.itemId, name: l.name, quantity: 0, amount: 0 }; entry.quantity += l.quantity; entry.amount += l.price * l.quantity; sales.set(l.itemId, entry) }))
    return clone({ revenue, paidOrders: paid.length, averageAmount: paid.length ? revenue / paid.length : 0, completedOrders: counts.COMPLETED,
      orderCount: created.length, cancellationRate: created.length ? counts.CANCELLED / created.length * 100 : 0,
      counts, trend, sales: [...sales.values()].sort((a, b) => b.quantity - a.quantity), period,
      metricNote: '演示口径：收入按支付时间统计当前已支付且未模拟退款的金额；订单量按创建时间；套餐作为独立商品统计，不拆分菜品。不是财务净收入。' })
  },
  async chat(message: string, sessionId: string): Promise<AgentReply> {
    const s = requireSession(); const db = database(); const reply: AgentReply = { sessionId: sessionId || crypto.randomUUID(), content: '', traces: [] }
    if (message.includes('下架')) {
      requireSession('admin')
      const item = db.menu.find(x => x.kind === 'setmeal' && message.includes(x.name))
      if (!item) { reply.content = '请提供完整套餐名称，例如“把元气鸡腿套餐下架”。'; return reply }
      if (!item.status) { reply.content = `${item.name}已经停售，无需重复操作。`; return reply }
      const id = crypto.randomUUID(); const expiresAt = Date.now() + 5 * 60000
      actions.set(id, { owner: s.id, role: s.role, kind: 'sale', targetId: item.id, previous: item.status, expiresAt, executed: false, cancelled: false })
      reply.content = '已生成待确认操作，尚未变更商品状态。'
      reply.traces = [{ name: 'prepareSetmealStatus', summary: '检查套餐与运营身份，生成 5 分钟有效的确认记录', state: 'success' }]
      reply.action = { id, label: `下架「${item.name}」`, detail: '确认后该套餐将停止销售，历史订单保持原有快照。', expiresAt: new Date(expiresAt).toISOString() }; return reply
    }
    if (message.includes('取消') && message.includes('订单')) {
      const page = await mock.orders({ status: 'UNPAID', pageSize: 20 })
      const order = page.records.find(x => message.includes(x.number))
      if (!order) { reply.content = '请提供需要取消的完整订单号。下方列出了可查看的待支付订单；当前尚未执行取消。'; reply.orders = page.records; return reply }
      const id = crypto.randomUUID(); const expiresAt = Date.now() + 5 * 60000
      actions.set(id, { owner: s.id, role: s.role, kind: 'cancel', targetId: order.id, previous: order.status, expiresAt, executed: false, cancelled: false })
      reply.content = '取消请求已准备，请检查订单号后确认。'; reply.action = { id, label: `取消订单 ${order.number}`, detail: `金额 ¥${order.amount.toFixed(2)}；取消后释放演示库存。`, expiresAt: new Date(expiresAt).toISOString() }; return reply
    }
    if (/营业额|销量|分析|经营|营收/.test(message)) {
      const metrics = await mock.metrics(); reply.metrics = metrics
      const top = metrics.sales[0]
      reply.content = `最近 7 天演示收入 ¥${metrics.revenue.toFixed(2)}，已支付订单 ${metrics.paidOrders} 笔，平均实付 ¥${metrics.averageAmount.toFixed(2)}。${top ? `销量最高的是${top.name}，共 ${top.quantity} 份。可结合备餐能力观察供应量，再决定是否调整。` : '当前没有销量数据。'}\n这是按模板生成的演示报告，未调用真实大模型；原因判断需要更多业务数据。`
      reply.traces = [{ name: 'queryBusinessMetrics', summary: '查询最近 7 天的演示支付金额与订单趋势', state: 'success' }, { name: 'queryDishSales', summary: '聚合演示订单商品销量', state: 'success' }]; return reply
    }
    if (/订单|上次|历史/.test(message)) {
      const orders = await mock.orders({ pageSize: 3 }); reply.orders = orders.records
      reply.traces.push({ name: 'queryOrder', summary: s.role === 'user' ? '以当前员工身份筛选最近订单' : '查询运营权限内的最近订单', state: 'success' })
      if (/上次|替代|便宜/.test(message) && orders.records[0]?.lines[0]) {
        const original = orders.records[0].lines[0]; const candidates = db.menu.filter(x => x.kind === 'setmeal' && x.price < original.price && availableQuantity(x, db.menu) > 0)
        reply.items = clone(candidates.slice(0, 3)); reply.traces.push({ name: 'querySetmeal', summary: `查询低于 ¥${original.price} 的可售套餐`, state: 'success' })
        reply.content = `最近订单中的${original.name}单价 ¥${original.price}。${candidates.length ? '下面是当前更便宜的可售套餐。' : '目前没有更便宜的可售套餐。'}`
      } else reply.content = orders.records.length ? '下面是当前身份可查看的最近订单。状态来自本地演示数据。' : '没有找到订单。'
      return reply
    }
    const budgetMatch = message.match(/(\d+(?:\.\d+)?)\s*(?:元|块)/)
    const budget = budgetMatch ? Number(budgetMatch[1]) : Infinity
    const kind = message.includes('菜品') || message.includes('单品') ? 'dish' : 'setmeal'
    const tags = ['不辣', '清淡', '素食', '高蛋白'].filter(t => message.includes(t))
    const candidates = db.menu.filter(x => x.kind === kind && x.price <= budget && availableQuantity(x, db.menu) > 0 && db.categories.some(c => c.id === x.categoryId && c.status === 1) && tags.every(t => x.tags.includes(t))).sort((a, b) => a.price - b.price)
    reply.items = clone(candidates.slice(0, 3))
    reply.traces = [{ name: kind === 'dish' ? 'searchDish' : 'querySetmeal', summary: `按${Number.isFinite(budget) ? budget + ' 元预算、' : ''}${tags.join('、') || '可售状态'}及演示库存筛选`, state: 'success' }]
    reply.content = candidates.length ? `为你找到 ${candidates.length} 个符合条件的${kind === 'dish' ? '菜品' : '套餐'}，这里展示价格较低的 ${Math.min(3, candidates.length)} 个。你可以加入购物车再确认下单。\n演示模式按规则筛选与价格排序，尚未接入 LLM 语义排序。` : '没有找到符合条件的可售商品。可以调整预算或口味条件；当前条件没有被自动放宽。'
    return reply
  },
  async confirmAction(id: string): Promise<{ content: string }> {
    const s = requireSession(); const action = actions.get(id)
    if (!action || action.owner !== s.id || action.role !== s.role) throw new Error('确认记录不存在或不属于当前身份')
    if (action.cancelled) throw new Error('本次操作已被放弃，请重新发起')
    if (action.executed) return { content: '该操作已执行，未重复变更数据。' }
    if (action.expiresAt <= Date.now()) throw new Error('确认已过期，请重新发起操作')
    if (action.kind === 'sale') {
      requireSession('admin'); const item = database().menu.find(x => x.id === action.targetId)
      if (!item || item.status !== action.previous) throw new Error('套餐状态已变化，请重新查询并确认')
      await mock.saveMenu({ ...item, status: 0 })
    } else {
      const order = ownedOrder(action.targetId)
      if (order.status !== action.previous) throw new Error('订单状态已变化，请重新查询并确认')
      await mock.orderAction(order.id, 'cancel')
    }
    action.executed = true; return { content: '确认成功，演示数据已更新。真实环境由你的后端执行。' }
  },
  async cancelAction(id: string): Promise<void> {
    const s = requireSession(); const action = actions.get(id)
    if (!action || action.owner !== s.id || action.role !== s.role) throw new Error('确认记录不存在或不属于当前身份')
    if (action.executed) throw new Error('操作已经执行，不能撤销确认记录')
    action.cancelled = true
  },
  async reset(): Promise<void> { requireSession('admin'); data = createSeed(); persist(); actions.clear(); notifyOrder() }
}

window.addEventListener('storage', event => {
  if (event.key === DATA_KEY) { data = load(); notifyOrder() }
})
