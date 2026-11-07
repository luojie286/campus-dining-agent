const config = require('./config')

let sessionToken = ''
let loginPromise = null

function createError(message, statusCode) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function send(path, method, data, token) {
  return new Promise((resolve, reject) => {
    const header = {
      'Content-Type': 'application/json'
    }
    if (token) header.token = token

    wx.request({
      url: config.baseUrl.replace(/\/+$/, '') + path,
      method,
      data,
      header,
      timeout: 10000,

      success(response) {
        const body = response.data

        if (response.statusCode < 200 || response.statusCode >= 300) {
          const message = body && body.msg
            ? body.msg
            : '接口请求失败（' + response.statusCode + '）'

          reject(createError(message, response.statusCode))
          return
        }

        if (!body || body.code !== 1) {
          reject(createError(
            body && body.msg ? body.msg : '接口返回格式异常',
            response.statusCode
          ))
          return
        }

        resolve(body.data)
      },

      fail() {
        reject(createError('无法连接后端，请检查服务地址和网络', 0))
      }
    })
  })
}

function getWechatCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (result.code) {
          resolve(result.code)
        } else {
          reject(new Error('微信没有返回登录凭证，请重试'))
        }
      },
      fail() {
        reject(new Error('微信登录失败，请重试'))
      }
    })
  })
}

async function getToken() {
  if (sessionToken) return sessionToken
  if (loginPromise) return loginPromise

  loginPromise = (async () => {
    const code = await getWechatCode()

    const session = await send(
      '/api/user/wechat/login',
      'POST',
      { code }
    )

    if (!session || session.role !== 'user' || !session.token) {
      throw new Error('后端登录响应缺少有效的点餐用户凭证')
    }

    sessionToken = session.token
    return sessionToken
  })()

  try {
    return await loginPromise
  } finally {
    loginPromise = null
  }
}

async function request(path, method = 'GET', data = {}) {
  const token = await getToken()

  try {
    return await send(path, method, data, token)
  } catch (error) {
    if (error.statusCode !== 401) throw error

    // 只清除本次失败请求使用的旧凭证。
    // 另一个请求可能已经完成重新登录，不能把它的新凭证清掉。
    if (sessionToken === token) sessionToken = ''

    const newToken = await getToken()
    try {
      return await send(path, method, data, newToken)
    } catch (retryError) {
      if (retryError.statusCode === 401 && sessionToken === newToken) sessionToken = ''
      throw retryError
    }
  }
}

module.exports = { request }
