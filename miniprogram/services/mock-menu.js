const scenes = [
  { value: 'normal', label: '正常营业' },
  { value: 'closed', label: '食堂打烊' },
  { value: 'empty', label: '暂无商品' },
  { value: 'error', label: '首次菜单请求失败' }
]

const shop = {
  name: '园味食堂',
  open: true,
  openingHours: '11:00–13:30 · 17:00–19:00',
  pickupPoint: '园区食堂一楼自提点'
}

const categories = [
  { id: 5, type: 2, name: '精选套餐', sort: 0, status: 1 },
  { id: 1, type: 1, name: '荤食主菜', sort: 1, status: 1 },
  { id: 2, type: 1, name: '时令时蔬', sort: 2, status: 1 },
  { id: 3, type: 1, name: '面食小吃', sort: 3, status: 1 },
  { id: 4, type: 1, name: '轻食沙拉', sort: 4, status: 1 }
]

function dish(id, categoryId, name, price, stock, art, tags, description) {
  return {
    id, kind: 'dish', categoryId, name, price, stock, art, tags, description,
    status: 1, composition: []
  }
}

const dishes = [
  dish(2, 1, '香煎鸡腿排', 18, 24, 'chicken', ['不辣', '高蛋白'], '鸡腿肉煎至外皮微脆，搭配当日时蔬。'),
  dish(4, 1, '清蒸鲜鱼', 22, 16, 'fish', ['不辣', '清淡'], '清蒸锁鲜，葱姜提香，适合偏好清淡口味的午餐。'),
  dish(6, 1, '黑椒牛肉', 24, 0, 'beef', ['微辣', '高蛋白'], '牛肉与彩椒快炒，黑椒香气浓郁。'),
  dish(8, 1, '照烧鸡腿肉', 19, 13, 'chicken', ['不辣'], '鸡腿肉搭配照烧汁与时蔬，咸甜适口。'),
  dish(10, 1, '葱香鱼片', 20, 11, 'fish', ['清淡', '高蛋白'], '鲜嫩鱼片配葱姜调味，清爽下饭。'),
  dish(12, 2, '清炒西兰花', 8, 18, 'salad', ['素食', '清淡'], '西兰花清炒，保留爽脆口感。'),
  dish(14, 2, '番茄炒蛋', 10, 20, 'salad', ['不辣'], '番茄的酸甜裹住软嫩鸡蛋。'),
  dish(16, 2, '蒜蓉青菜', 7, 16, 'salad', ['素食'], '时令青菜与蒜蓉一起快炒。'),
  dish(18, 3, '番茄鸡蛋面', 16, 12, 'noodle', ['不辣'], '番茄熬制汤底，搭配鸡蛋与面条。'),
  dish(20, 3, '黑椒牛肉拌面', 21, 8, 'noodle', ['微辣'], '劲道面条拌入黑椒牛肉和蔬菜。'),
  dish(22, 3, '葱油拌面', 11, 15, 'noodle', ['不辣'], '葱油裹面，鲜香爽滑。'),
  dish(24, 4, '田园蔬菜沙拉', 12, 22, 'salad', ['素食', '清淡'], '生菜、玉米和番茄搭配油醋汁。'),
  dish(26, 4, '鸡肉蔬菜碗', 20, 9, 'chicken', ['高蛋白', '清淡'], '鸡肉搭配新鲜蔬菜与谷物。'),
  dish(28, 4, '玉米蔬菜沙拉', 14, 15, 'salad', ['素食', '不辣'], '甜玉米、番茄与嫩叶蔬菜搭配。')
]

const setmeals = [
  {
    id: 3, kind: 'setmeal', categoryId: 5, name: '元气鸡腿套餐',
    price: 24, stock: 20, art: 'chicken', tags: ['不辣', '高蛋白'],
    description: '香煎鸡腿排配清炒西兰花。',
    status: 1, composition: [{ dishId: 2, quantity: 1 }, { dishId: 12, quantity: 1 }]
  },
  {
    id: 5, kind: 'setmeal', categoryId: 5, name: '清蒸鲜鱼套餐',
    price: 28, stock: 12, art: 'fish', tags: ['清淡'],
    description: '清蒸鲜鱼配田园蔬菜沙拉。',
    status: 1, composition: [{ dishId: 4, quantity: 1 }, { dishId: 24, quantity: 1 }]
  },
  {
    id: 7, kind: 'setmeal', categoryId: 5, name: '黑椒牛肉套餐',
    price: 30, stock: 8, art: 'beef', tags: ['微辣'],
    description: '黑椒牛肉配番茄炒蛋。',
    status: 1, composition: [{ dishId: 6, quantity: 1 }, { dishId: 14, quantity: 1 }]
  }
]

const dishById = new Map(dishes.map(item => [item.id, item]))
const menu = [...setmeals, ...dishes]

function toMenuItem(item) {
  let availableStock = item.stock
  // 用完整演示目录解析组成，所需菜品可能不在当前页。
  const compositionItems = item.composition.map(part => {
    const dish = dishById.get(part.dishId)
    const portions = dish && dish.status === 1 ? Math.floor(dish.stock / part.quantity) : 0
    availableStock = Math.min(availableStock, portions)
    return { ...part, name: dish ? dish.name : '组成菜品暂不可售' }
  })
  return { ...item, availableStock, compositionItems }
}

function wait() {
  return new Promise(resolve => setTimeout(resolve, 240))
}

// 页面只消费三个查询方法。联调时将本模块替换为按合同解包 Result 的请求实现。
function createMenuSource(scene = 'normal') {
  let failFirstPage = scene === 'error'
  return {
    async getShop() {
      await wait()
      return { ...shop, open: scene !== 'closed' }
    },
    async getCategories() {
      await wait()
      return categories.map(category => ({ ...category }))
    },
    async getMenuPage({ page, pageSize, categoryId }) {
      await wait()
      if (failFirstPage) {
        failFirstPage = false
        throw new Error('演示请求失败，请重试。')
      }

      const available = scene === 'empty' ? [] : menu.filter(item =>
        categoryId === undefined || item.categoryId === categoryId
      )
      const offset = (page - 1) * pageSize
      return { total: available.length, records: available.slice(offset, offset + pageSize).map(toMenuItem) }
    }
  }
}

module.exports = { createMenuSource, scenes }
