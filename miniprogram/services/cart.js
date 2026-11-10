const config = require('./config')
const { request } = require('./request')

const CART_PATH = '/api/user/shoppingCart'
const pendingKeys = new Map()
let demoLines = []

function createKey() {
  return 'cart-' + Date.now().toString(36) + '-' +
    Math.random().toString(36).slice(2).padEnd(10, '0')
}

function list() {
  if (config.mode === 'mock') return Promise.resolve(demoLines)
  return request(CART_PATH + '/list')
}

async function add(itemId) {
  if (config.mode === 'mock') {
    const existing = demoLines.find(line => line.itemId === itemId)
    if (existing && existing.quantity >= 99) throw new Error('单个商品最多99份')
    demoLines = existing
      ? demoLines.map(line => line.itemId === itemId ? { ...line, quantity: line.quantity + 1 } : line)
      : [...demoLines, { itemId, quantity: 1 }]
    return demoLines
  }
  // 超时可能发生在服务端提交之后；再次点击同一商品时继续使用原键。
  const key = pendingKeys.get(itemId) || createKey()
  pendingKeys.set(itemId, key)
  const result = await request(CART_PATH + '/add', 'POST', { itemId, quantity: 1 }, { 'Idempotency-Key': key })
  pendingKeys.delete(itemId)
  return result
}

async function remove(itemId) {
  if (config.mode === 'mock') {
    demoLines = demoLines.filter(line => line.itemId !== itemId)
    return demoLines
  }
  const result = await request(CART_PATH + '/' + itemId, 'DELETE')
  pendingKeys.delete(itemId)
  return result
}

async function clear() {
  if (config.mode === 'mock') {
    demoLines = []
    return demoLines
  }
  const result = await request(CART_PATH + '/clean', 'DELETE')
  pendingKeys.clear()
  return result
}

module.exports = { add, list, remove, clear }
