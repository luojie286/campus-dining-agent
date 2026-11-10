const config = require('./config')
const { request } = require('./request')

const path = '/api/user/addressBook'
let demo = []
let nextId = 1

function list() {
  if (config.mode === 'mock') {
    return Promise.resolve([...demo].sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || b.id - a.id))
  }
  return request(path + '/list')
}

function save(address) {
  if (config.mode !== 'mock') {
    return request(path, address.id ? 'PUT' : 'POST', address)
  }
  if (!address.id && demo.length >= 20) return Promise.reject(new Error('最多保存20条地址'))
  const saved = { ...address, id: address.id || nextId++ }
  demo = demo.filter(item => item.id !== saved.id).map(item =>
    saved.isDefault ? { ...item, isDefault: false } : item)
  demo.push(saved)
  return Promise.resolve(saved)
}

function setDefault(id) {
  if (config.mode !== 'mock') return request(path + '/' + id + '/default', 'PUT', {})
  demo = demo.map(item => ({ ...item, isDefault: item.id === id }))
  return Promise.resolve(demo.find(item => item.id === id))
}

function remove(id) {
  if (config.mode !== 'mock') return request(path + '/' + id, 'DELETE')
  demo = demo.filter(item => item.id !== id)
  return Promise.resolve()
}

module.exports = { list, save, setDefault, remove }
