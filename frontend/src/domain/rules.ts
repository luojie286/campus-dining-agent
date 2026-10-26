import type { CartLine, MenuItem, OrderStatus } from './types'

export function availableQuantity(item: MenuItem, menu: MenuItem[]): number {
  if (!item.status) return 0
  if (item.kind === 'dish') return item.stock
  if (!item.composition.length) return 0
  return Math.min(item.stock, ...item.composition.map(part => {
    const dish = menu.find(x => x.id === part.dishId && x.kind === 'dish')
    return dish?.status && part.quantity > 0 ? Math.floor(dish.stock / part.quantity) : 0
  }))
}

/** 演示前端的库存规则；真实环境以你编写的后端事务为准。 */
export function inventoryRequirements(cart: CartLine[], menu: MenuItem[]): Map<number, number> {
  const needed = new Map<number, number>()
  const add = (id: number, n: number) => needed.set(id, (needed.get(id) || 0) + n)
  if (!cart.length) throw new Error('购物车为空')
  for (const line of cart) {
    if (!Number.isInteger(line.quantity) || line.quantity <= 0 || line.quantity > 99) throw new Error('商品数量必须为 1–99 的整数')
    const item = menu.find(x => x.id === line.itemId)
    if (!item || !item.status || availableQuantity(item, menu) < line.quantity) throw new Error('部分商品已停售或库存不足，请调整购物车')
    add(item.id, line.quantity)
    if (item.kind === 'setmeal') item.composition.forEach(part => add(part.dishId, part.quantity * line.quantity))
  }
  for (const [id, n] of needed) {
    if ((menu.find(x => x.id === id)?.stock ?? 0) < n) throw new Error('套餐与单品的共享库存不足，请减少数量')
  }
  return needed
}

const transitions: Record<OrderStatus, OrderStatus[]> = {
  UNPAID: ['PREPARING', 'CANCELLED'], PREPARING: ['READY_DELIVERY', 'READY_PICKUP', 'CANCELLED'],
  READY_DELIVERY: ['DELIVERING', 'CANCELLED'], DELIVERING: ['COMPLETED'], READY_PICKUP: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [], CANCELLED: []
}
export function canTransition(from: OrderStatus, to: OrderStatus): boolean { return transitions[from].includes(to) }
