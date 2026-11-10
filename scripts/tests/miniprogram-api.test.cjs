const assert = require('node:assert/strict')
const { test } = require('node:test')
const path = require('node:path')

const services = path.resolve(__dirname, '../../miniprogram/services')
const shop = { name: '接口测试食堂', open: true, openingHours: '11:00–13:00', pickupPoint: '一楼' }
const ok = data => ({ statusCode: 200, data: { code: 1, data } })

function setup(handler) {
  for (const name of ['menu', 'request', 'api-menu', 'mock-menu', 'config', 'cart', 'orders']) {
    delete require.cache[require.resolve(path.join(services, name))]
  }
  let logins = 0
  const calls = []
  global.wx = {
    login(options) {
      logins++
      queueMicrotask(() => options.success({ code: 'fresh-code-' + logins }))
    },
    request(options) {
      calls.push(options)
      Promise.resolve().then(() => handler(options)).then(
        response => options.success(response),
        () => options.fail()
      )
    }
  }
  const source = require(path.join(services, 'menu')).createMenuSource()
  return { source, calls, loginCount: () => logins }
}

function loginResponse(options) {
  assert.equal(options.method, 'POST')
  assert(!options.header.token)
  return ok({ id: 3, role: 'user', token: 'token-' + options.data.code })
}

test('并发读取店铺和分类仅微信登录一次，携带本站token', async () => {
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    assert.equal(options.header.token, 'token-fresh-code-1')
    return ok(options.url.endsWith('/shop') ? shop : [])
  })
  const [actualShop, categories] = await Promise.all([context.source.getShop(), context.source.getCategories()])
  assert.deepEqual(actualShop, shop)
  assert.deepEqual(categories, [])
  assert.equal(context.loginCount(), 1)
})

test('菜单保留分页和分类参数，将服务端相对图片补成完整地址', async () => {
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    return ok({
      total: 2,
      records: [
        { id: 2, availableStock: 5, image: '/api/media/product-images/a.jpg' },
        { id: 4, availableStock: 0, image: 'https://cdn.example.invalid/b.jpg' }
      ]
    })
  })
  const page = await context.source.getMenuPage({ page: 1, pageSize: 6 })
  assert.equal(page.records[0].image, 'http://127.0.0.1:8080/api/media/product-images/a.jpg')
  assert.equal(page.records[1].image, 'https://cdn.example.invalid/b.jpg')
  const request = context.calls.at(-1)
  assert.deepEqual(request.data, { page: 1, pageSize: 6 })
  await context.source.getMenuPage({ page: 2, pageSize: 6, categoryId: 5 })
  assert.deepEqual(context.calls.at(-1).data, { page: 2, pageSize: 6, categoryId: 5 })
})

test('旧token的并发401只重新登录一次，重试最多一次', async () => {
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    if (options.header.token === 'token-fresh-code-1') {
      return { statusCode: 401, data: { code: 0, msg: '凭证过期' } }
    }
    return ok(shop)
  })
  await Promise.all([context.source.getShop(), context.source.getShop()])
  assert.equal(context.loginCount(), 2)
  assert.equal(context.calls.filter(call => call.url.endsWith('/shop')).length, 4)

  const rejected = setup(options => options.url.endsWith('/wechat/login')
    ? loginResponse(options)
    : { statusCode: 401, data: { code: 0, msg: '凭证被拒绝' } })
  await assert.rejects(rejected.source.getShop(), /凭证被拒绝/)
  assert.equal(rejected.loginCount(), 2)
  assert.equal(rejected.calls.length, 4)
})

test('微信登录失败后可重试，权限错误不触发重新登录', async () => {
  let failLogin = true
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) {
      if (failLogin) {
        failLogin = false
        return { statusCode: 503, data: { code: 0, msg: '微信登录暂不可用' } }
      }
      return loginResponse(options)
    }
    return { statusCode: 403, data: { code: 0, msg: '账号停用' } }
  })
  await assert.rejects(context.source.getShop(), /微信登录暂不可用/)
  assert.equal(context.calls.length, 1)
  await assert.rejects(context.source.getShop(), /账号停用/)
  assert.equal(context.loginCount(), 2)
})

test('缺失可售量时明确报合同错误，不以演示数据替代', async () => {
  const context = setup(options => options.url.endsWith('/wechat/login')
    ? loginResponse(options)
    : ok({ total: 1, records: [{ id: 2, stock: 9 }] }))
  await assert.rejects(context.source.getMenuPage({ page: 1, pageSize: 6 }), /availableStock/)
  assert.equal(context.calls.length, 2)
})

test('显式演示模式不调用wx登录和HTTP', async () => {
  const context = setup(() => { throw new Error('不应发起HTTP') })
  const config = require(path.join(services, 'config'))
  config.mode = 'mock'
  const source = require(path.join(services, 'menu')).createMenuSource()
  const page = await source.getMenuPage({ page: 1, pageSize: 6 })
  assert(page.total > 0)
  assert.equal(page.records.length, 6)
  assert.equal(context.loginCount(), 0)
  assert.equal(context.calls.length, 0)
  config.mode = 'api'
})

test('加购连接超时后复用幂等键，成功后的新点击使用新键', async () => {
  let failOnce = true
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    if (failOnce) {
      failOnce = false
      throw new Error('响应丢失')
    }
    return ok([{ itemId: 2, quantity: 1 }])
  })
  const cart = require(path.join(services, 'cart'))
  await assert.rejects(cart.add(2), /无法连接后端/)
  await cart.add(2)
  await cart.add(2)
  const requests = context.calls.filter(call => call.url.endsWith('/shoppingCart/add'))
  const keys = requests.map(call => call.header['Idempotency-Key'])
  assert.equal(keys[0], keys[1])
  assert.notEqual(keys[1], keys[2])
  assert.match(keys[0], /^[A-Za-z0-9_-]{16,64}$/)
  assert.deepEqual(requests[0].data, { itemId: 2, quantity: 1 })
})

test('订单响应丢失时重试沿用原下单键', async () => {
  let lost = true
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    if (lost) {
      lost = false
      throw new Error('响应丢失')
    }
    return ok({ id: 13, status: 'UNPAID' })
  })
  const orders = require(path.join(services, 'orders'))
  const body = { fulfillment: 'DELIVERY', remark: '', addressId: 1 }
  await assert.rejects(orders.submit(body), /无法连接后端/)
  assert.equal((await orders.submit(body)).id, 13)
  const submits = context.calls.filter(call => call.url.endsWith('/order/submit'))
  assert.equal(submits[0].header['Idempotency-Key'], submits[1].header['Idempotency-Key'])
  assert.deepEqual(submits[1].data, body)
})

test('历史订单保留筛选参数，再来一单失败后复用请求键', async () => {
  let lost = true
  const context = setup(options => {
    if (options.url.endsWith('/wechat/login')) return loginResponse(options)
    if (options.url.endsWith('/order/page')) return ok({ total: 0, records: [] })
    if (lost) {
      lost = false
      throw new Error('响应丢失')
    }
    return ok([{ itemId: 2, quantity: 1 }])
  })
  const orders = require(path.join(services, 'orders'))
  await orders.page({ page: 2, pageSize: 8, status: 'COMPLETED' })
  assert.deepEqual(context.calls.at(-1).data, { page: 2, pageSize: 8, status: 'COMPLETED' })
  await assert.rejects(orders.reorder(3), /无法连接后端/)
  await orders.reorder(3)
  const calls = context.calls.filter(call => call.url.endsWith('/order/3/reorder'))
  assert.equal(calls[0].header['Idempotency-Key'], calls[1].header['Idempotency-Key'])
})
