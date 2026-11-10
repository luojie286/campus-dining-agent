const { request } = require('./request')

let pending = null
const reorderKeys = new Map()

async function submit(body) {
  const signature = JSON.stringify(body)
  if (!pending || pending.signature !== signature) {
    const random = Math.random().toString(36).slice(2).padEnd(12, '0')
    pending = { signature, key: 'order-' + Date.now().toString(36) + '-' + random }
  }
  const current = pending
  const order = await request('/api/user/order/submit', 'POST', body, { 'Idempotency-Key': current.key })
  if (pending === current) pending = null
  return order
}

function detail(id) {
  return request('/api/user/order/' + id)
}

function payMock(id) {
  return request('/api/user/order/' + id + '/pay', 'POST')
}

function cancel(id) {
  return request('/api/user/order/' + id + '/cancel', 'POST')
}

function payment(id) {
  return request('/api/user/order/' + id + '/payment')
}

function page(query) {
  return request('/api/user/order/page', 'GET', query)
}

async function reorder(id) {
  let key = reorderKeys.get(id)
  if (!key) {
    key = 'reorder-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2).padEnd(12, '0')
    reorderKeys.set(id, key)
  }
  const lines = await request('/api/user/order/' + id + '/reorder', 'POST', {}, { 'Idempotency-Key': key })
  reorderKeys.delete(id)
  return lines
}

module.exports = { submit, detail, payMock, cancel, payment, page, reorder }
