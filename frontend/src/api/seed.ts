import type { Address, CartLine, Category, MenuItem, Operator, Order, OrderStatus, Shop } from '@/domain/types'

export interface DemoData {
  version: number; categories: Category[]; menu: MenuItem[]; operators: Operator[]; addresses: Address[]
  orders: Order[]; carts: Record<string, CartLine[]>; shop: Shop
  operatorCredentials?: Record<string, { salt: number[]; hash: string }>
}

export function createSeed(): DemoData {
  const categories: Category[] = [
    { id: 1, name: '荤食主菜', type: 1, sort: 1, status: 1 },
    { id: 2, name: '轻食时蔬', type: 1, sort: 2, status: 1 },
    { id: 3, name: '面食小吃', type: 1, sort: 3, status: 1 },
    { id: 4, name: '人气套餐', type: 2, sort: 4, status: 1 },
    { id: 5, name: '轻盈套餐', type: 2, sort: 5, status: 1 }
  ]
  const menu: MenuItem[] = [
    { id: 101, kind: 'dish', name: '香煎鸡腿排', categoryId: 1, price: 18, description: '现煎鸡腿肉，搭配当日时蔬。外皮微脆，鲜嫩多汁。', status: 1, stock: 45, tags: ['不辣', '高蛋白'], art: 'chicken', composition: [] },
    { id: 102, kind: 'dish', name: '清蒸鲜鱼', categoryId: 1, price: 22, description: '清蒸锁鲜，葱姜提香。适合偏好清淡口味的午餐。', status: 1, stock: 28, tags: ['不辣', '清淡'], art: 'fish', composition: [] },
    { id: 103, kind: 'dish', name: '黑椒牛肉', categoryId: 1, price: 24, description: '牛肉与彩椒一起快炒，黑椒香气浓郁。', status: 1, stock: 30, tags: ['微辣', '高蛋白'], art: 'beef', composition: [] },
    { id: 104, kind: 'dish', name: '田园蔬菜沙拉', categoryId: 2, price: 12, description: '新鲜生菜、玉米与番茄，搭配油醋汁。', status: 1, stock: 55, tags: ['不辣', '素食', '清淡'], art: 'salad', composition: [] },
    { id: 105, kind: 'dish', name: '番茄鸡蛋面', categoryId: 3, price: 16, description: '番茄熬制汤底，配鸡蛋与手工面条。', status: 1, stock: 40, tags: ['不辣', '素食'], art: 'noodle', composition: [] },
    { id: 106, kind: 'dish', name: '麻婆豆腐', categoryId: 1, price: 14, description: '豆腐与肉末慢煨，香辣下饭。', status: 0, stock: 0, tags: ['辣'], art: 'tofu', composition: [] },
    { id: 201, kind: 'setmeal', name: '元气鸡腿套餐', categoryId: 4, price: 28, description: '香煎鸡腿排 + 田园蔬菜沙拉 + 米饭，午间均衡搭配。', status: 1, stock: 32, tags: ['不辣', '高蛋白'], art: 'chicken', composition: [{ dishId: 101, quantity: 1 }, { dishId: 104, quantity: 1 }] },
    { id: 202, kind: 'setmeal', name: '清蒸鲜鱼套餐', categoryId: 4, price: 30, description: '清蒸鲜鱼 + 田园蔬菜沙拉 + 米饭，清淡也有好滋味。', status: 1, stock: 22, tags: ['不辣', '清淡'], art: 'fish', composition: [{ dishId: 102, quantity: 1 }, { dishId: 104, quantity: 1 }] },
    { id: 203, kind: 'setmeal', name: '黑椒牛肉套餐', categoryId: 4, price: 35, description: '黑椒牛肉 + 田园沙拉 + 米饭，满足一餐的好胃口。', status: 1, stock: 20, tags: ['微辣', '高蛋白'], art: 'beef', composition: [{ dishId: 103, quantity: 1 }, { dishId: 104, quantity: 1 }] },
    { id: 204, kind: 'setmeal', name: '轻盈田园套餐', categoryId: 5, price: 25, description: '田园蔬菜沙拉 + 番茄鸡蛋面，轻松享用一顿午餐。', status: 1, stock: 26, tags: ['不辣', '素食', '清淡'], art: 'salad', composition: [{ dishId: 104, quantity: 1 }, { dishId: 105, quantity: 1 }] }
  ]
  const now = new Date()
  const orders: Order[] = []
  let id = 3001
  for (let day = 6; day >= 0; day--) {
    for (let n = 0; n < 5 + ((day * 3) % 7); n++) {
      const item = menu[6 + (n % 4)]!
      const time = new Date(now); time.setDate(now.getDate() - day); time.setHours(10 + (n % 3), 12 + n, 0, 0)
      // 今日演示记录不设置到未来。
      if (time.getTime() > now.getTime()) time.setTime(now.getTime() - (n + 1) * 60000)
      const status: OrderStatus = day === 0 && n === 0 ? 'PREPARING' : day === 0 && n === 1 ? 'READY_PICKUP' : day === 0 && n === 2 ? 'UNPAID' : 'COMPLETED'
      const paid = status !== 'UNPAID'
      const quantity = n % 4 === 0 ? 2 : 1
      orders.push({
        id: id++, number: `YW${time.getFullYear()}${String(time.getMonth() + 1).padStart(2, '0')}${String(time.getDate()).padStart(2, '0')}${id}`,
        userId: n % 3 === 0 ? 1002 : 1001, userName: n % 3 === 0 ? '陈可' : '林同学', status,
        payStatus: paid ? 'PAID' : 'UNPAID', fulfillment: n % 2 === 0 ? 'DELIVERY' : 'PICKUP',
        amount: item.price * quantity, createdAt: time.toISOString(), paidAt: paid ? time.toISOString() : undefined,
        paymentDeadline: new Date(now.getTime() + 15 * 60000).toISOString(),
        recipient: n % 3 === 0 ? '陈可' : '林同学', phone: '13800000001', address: n % 2 === 0 ? 'A 座 8 楼 · 产品研发部' : '园区食堂一楼自提点',
        remark: '', lines: [{ itemId: item.id, name: item.name, kind: item.kind, price: item.price, quantity }],
        events: [{ status: 'UNPAID', time: time.toISOString(), note: '演示订单创建' }, ...(paid ? [{ status, time: time.toISOString(), note: '演示历史状态' }] : [])]
      })
    }
  }
  return {
    version: 1, categories, menu, orders, carts: {},
    operators: [{ id: 1, username: 'admin', name: '运营管理员', phone: '13800000000', status: 1, accountRole: 'ACCOUNT_ADMIN' }, { id: 2, username: 'operator', name: '食堂运营', phone: '13800000002', status: 1, accountRole: 'OPERATOR' }],
    addresses: [{ id: 1, userId: 1001, consignee: '林同学', phone: '13800000001', building: 'A 座', detail: '8 楼 · 产品研发部', isDefault: true }],
    shop: { name: '园区食堂', open: true, openingHours: '10:30 — 13:30 · 17:00 — 19:00', pickupPoint: '园区食堂一楼自提点' }
  }
}
