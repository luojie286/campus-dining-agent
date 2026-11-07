const config = require('./config')
const mock = require('./mock-menu')
const { createApiMenuSource } = require('./api-menu')

function createMenuSource(scene = 'normal') {
  if (config.mode === 'mock') return mock.createMenuSource(scene)
  if (config.mode === 'api') return createApiMenuSource()
  throw new Error('菜单模式必须配置为 api 或 mock')
}

module.exports = { createMenuSource, scenes: mock.scenes }
