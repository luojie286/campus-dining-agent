import { API_MODE, readSession, request } from './http'
import { mock } from './mock'
import { initialPasswordError } from '@/domain/operatorPassword'
import { menuFormError } from '@/domain/menuForm'
import type { Address, AgentReply, CartLine, Category, CategoryPageQuery, Dashboard, ListQuery, MenuItem, Operator, OperatorPageQuery, Order, Page, Role, Session, Shop } from '@/domain/types'

// 所有真实接口集中于此；API_CONTRACT.md 与此文件保持一致。
const rolePath = () => readSession()?.role === 'admin' ? '/admin' : '/user'
const select = <T>(demo: () => Promise<T>, real: () => Promise<T>): Promise<T> => API_MODE === 'mock' ? demo() : real()

async function menuItem(kind: MenuItem['kind'], id: number): Promise<MenuItem> {
  return select(() => mock.menuItem(kind, id), () => request<MenuItem>(`/admin/${kind}/${id}`))
}

async function saveMenu(item: MenuItem): Promise<MenuItem> {
  const error = menuFormError(item)
  if (error) throw new Error(error)
  // 显式白名单；编辑不携带库存，也不原样转发详情中的额外字段。
  const { id, kind, categoryId, price, description, status, art } = item
  const input = { id, kind, name: item.name.trim(), categoryId, price, description, status,
    tags: [...item.tags], art, composition: item.composition.map(part => ({ ...part })) }
  const body: typeof input & { stock?: number; image?: string } = { ...input }
  if (!id) body.stock = item.stock
  if (item.image !== undefined) body.image = item.image
  return select(() => mock.saveMenu({ ...body, stock: item.stock }),
    () => request<MenuItem>(`/admin/${kind}`, id ? 'PUT' : 'POST', body))
}

async function uploadDishImage(file: File): Promise<string> {
  if (API_MODE === 'mock') {
    // 演示数据保存在浏览器内，缩小照片以免很快占满本地存储。
    const image = await createImageBitmap(file)
    try {
      const scale = Math.min(1, 900 / Math.max(image.width, image.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(image.width * scale))
      canvas.height = Math.max(1, Math.round(image.height * scale))
      const context = canvas.getContext('2d')
      if (!context) throw new Error('当前浏览器无法保存演示图片')
      context.fillStyle = '#fff'
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.drawImage(image, 0, 0, canvas.width, canvas.height)
      return canvas.toDataURL('image/jpeg', 0.8)
    } finally {
      image.close()
    }
  }

  const body = new FormData()
  body.append('file', file)
  const result = await request<{ url: string }>('/admin/common/upload', 'POST', body)
  return result.url
}

export const api = {
  login: (role: Role, username: string, password: string) => select(() => mock.login(role, username, password), () => request<Session>(role === 'admin' ? '/admin/employee/login' : '/user/login', 'POST', { username, password })),
  shop: () => select(mock.shop, () => request<Shop>(`${rolePath()}/shop`)),
  setShop: (open: boolean) => select(() => mock.setShop(open), () => request<Shop>('/admin/shop', 'PUT', { open })),
  menu: (kind?: 'dish' | 'setmeal', query: ListQuery = {}) => select(() => mock.menu(kind, query), () => request<Page<MenuItem>>(`${rolePath()}/${kind || 'menu'}/page`, 'GET', undefined, { ...query })),
  menuItem,
  saveMenu,
  uploadDishImage,
  setMenuStatus: async (item: MenuItem, status: MenuItem['status']): Promise<MenuItem> => {
    if (item.kind === 'setmeal') {
      return select(async () => mock.saveMenu({ ...await mock.menuItem('setmeal', item.id), status }),
        () => request<MenuItem>(`/admin/setmeal/${item.id}/status`, 'PUT', { status }))
    }
    // 菜品后端专用状态接口尚未实现，兼容PUT前先读最新资料。
    const current = await menuItem(item.kind, item.id)
    return saveMenu({ ...current, status })
  },
  deleteMenu: (item: MenuItem) => select(() => mock.deleteMenu(item.id), () => request<void>(`/admin/${item.kind}/${item.id}`, 'DELETE')),
  categories: () => select(mock.categories, () => request<Category[]>(`${rolePath()}/category/list`)),
  categoriesPage: (query: CategoryPageQuery = {}) => select(
    () => mock.categoriesPage(query),
    () => request<Page<Category>>('/admin/category/page', 'GET', undefined, { ...query })
  ),
  setCategoryStatus: (id: number, status: Category['status']) => select(
    () => mock.setCategoryStatus(id, status),
    () => request<Category>(`/admin/category/${id}/status`, 'PUT', { status })
  ),
  saveCategory: (category: Category) => select(() => mock.saveCategory(category), () => request<Category>('/admin/category', category.id ? 'PUT' : 'POST', category)),
  deleteCategory: (id: number) => select(() => mock.deleteCategory(id), () => request<void>(`/admin/category/${id}`, 'DELETE')),
  operators: () => select(mock.operators, () => request<Operator[]>('/admin/employee/list')),
  operatorsPage: (query: OperatorPageQuery = {}) => select(() => mock.operatorsPage(query), () => request<Page<Operator>>('/admin/employee/page', 'GET', undefined, { ...query })),
  operator: (id: number) => select(() => mock.operator(id), () => request<Operator>(`/admin/employee/${id}`)),
  setOperatorStatus: (id: number, status: Operator['status']) => select(() => mock.setOperatorStatus(id, status), () => request<Operator>(`/admin/employee/${id}/status`, 'PUT', { status })),
  saveOperator: async (operator: Operator, initialPassword?: string) => {
    const { id, username, name, phone, status } = operator
    if (id) {
      // 编辑只提交可修改的资料，避免旧表单覆盖账号状态。
      const input = { id, name: name.trim(), phone }
      return select(
        () => mock.updateOperator(input),
        () => request<Operator>('/admin/employee', 'PUT', input)
      )
    }

    const password = initialPassword ?? ''
    const error = initialPasswordError(password)
    if (error) throw new Error(error)

    // 角色由后端决定；初始状态和密码仅在新增时提交。
    const input = { id, username: username.trim(), name: name.trim(), phone, status }
    const body = { ...input, initialPassword: password }
    return select(() => mock.saveOperator(input, password), () => request<Operator>('/admin/employee', 'POST', body))
  },
  addresses: () => select(mock.addresses, () => request<Address[]>('/user/addressBook/list')),
  saveAddress: (address: Address) => select(() => mock.saveAddress(address), () => request<Address>('/user/addressBook', address.id ? 'PUT' : 'POST', address)),
  deleteAddress: (id: number) => select(() => mock.deleteAddress(id), () => request<void>(`/user/addressBook/${id}`, 'DELETE')),
  cart: () => select(mock.cart, () => request<CartLine[]>('/user/shoppingCart/list')),
  setCart: (lines: CartLine[]) => select(() => mock.setCart(lines), () => request<CartLine[]>('/user/shoppingCart', 'PUT', { lines })),
  submitOrder: (input: { fulfillment: 'DELIVERY' | 'PICKUP'; addressId?: number; remark: string }) => select(() => mock.submitOrder(input), () => request<Order>('/user/order/submit', 'POST', input)),
  orders: (query: ListQuery = {}) => select(() => mock.orders(query), () => request<Page<Order>>(`${rolePath()}/order/page`, 'GET', undefined, { ...query })),
  orderCounts: (query: Pick<ListQuery, 'name' | 'begin' | 'end'> = {}) => request<Record<string, number>>('/admin/order/counts', 'GET', undefined, { ...query }),
  order: (id: number) => select(() => mock.order(id), () => request<Order>(`${rolePath()}/order/${id}`)),
  orderAction: (id: number, action: 'pay' | 'cancel' | 'ready' | 'deliver' | 'complete') => select(() => mock.orderAction(id, action), () => request<Order>(`${rolePath()}/order/${id}/${action}`, 'POST')),
  metrics: (begin?: string, end?: string) => select(() => mock.metrics(begin, end), () => request<Dashboard>('/admin/report/overview', 'GET', undefined, { begin, end })),
  chat: (message: string, sessionId: string) => select(() => mock.chat(message, sessionId), () => request<AgentReply>('/agent/chat', 'POST', { message, sessionId })),
  confirmAction: (id: string) => select(() => mock.confirmAction(id), () => request<{ content: string }>(`/agent/actions/${encodeURIComponent(id)}/confirm`, 'POST', {})),
  cancelAction: (id: string) => select(() => mock.cancelAction(id), () => request<void>(`/agent/actions/${encodeURIComponent(id)}/cancel`, 'POST', {})),
  resetDemo: () => {
    if (API_MODE !== 'mock') return Promise.reject(new Error('真实接口模式不能重置演示数据'))
    return mock.reset()
  }
}
