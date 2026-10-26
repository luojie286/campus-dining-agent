import { API_MODE, readSession, request } from './http'
import { mock } from './mock'
import { initialPasswordError } from '@/domain/operatorPassword'
import type { Address, AgentReply, CartLine, Category, CategoryPageQuery, Dashboard, ListQuery, MenuItem, Operator, OperatorPageQuery, Order, Page, Role, Session, Shop } from '@/domain/types'

// 所有真实接口集中于此；API_CONTRACT.md 与此文件保持一致。
const rolePath = () => readSession()?.role === 'admin' ? '/admin' : '/user'
const select = <T>(demo: () => Promise<T>, real: () => Promise<T>): Promise<T> => API_MODE === 'mock' ? demo() : real()
export const api = {
  login: (role: Role, username: string, password: string) => select(() => mock.login(role, username, password), () => request<Session>(role === 'admin' ? '/admin/employee/login' : '/user/login', 'POST', { username, password })),
  shop: () => select(mock.shop, () => request<Shop>(`${rolePath()}/shop`)),
  setShop: (open: boolean) => select(() => mock.setShop(open), () => request<Shop>('/admin/shop', 'PUT', { open })),
  menu: (kind?: 'dish' | 'setmeal', query: ListQuery = {}) => select(() => mock.menu(kind, query), () => request<Page<MenuItem>>(`${rolePath()}/${kind || 'menu'}/page`, 'GET', undefined, { ...query })),
  saveMenu: (item: MenuItem) => select(() => mock.saveMenu(item), () => request<MenuItem>(`/admin/${item.kind}`, item.id ? 'PUT' : 'POST', item)),
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
