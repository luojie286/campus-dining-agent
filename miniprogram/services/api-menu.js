const config = require('./config')
const { request } = require('./request')

const baseUrl = config.baseUrl.replace(/\/+$/, '')

function resolveImageUrl(image) {
  if (!image) return undefined
  if (/^https?:\/\//i.test(image)) return image
  return baseUrl + (image.startsWith('/') ? '' : '/') + image
}

function createApiMenuSource() {
  return {
    getShop() {
      return request('/api/user/shop')
    },

    getCategories() {
      // 省略type，返回菜品与套餐的启用分类。
      return request('/api/user/category/list')
    },

    async getMenuPage(query) {
      const params = { page: query.page, pageSize: query.pageSize }
      if (query.categoryId !== undefined) params.categoryId = query.categoryId

      const page = await request('/api/user/menu/page', 'GET', params)
      if (!page || !Number.isInteger(page.total) || page.total < 0 || !Array.isArray(page.records)) {
        throw new Error('菜单接口应返回 total 和 records，请检查后端响应')
      }

      return {
        total: page.total,
        records: page.records.map(item => {
          if (!Number.isInteger(item.availableStock) || item.availableStock < 0) {
            throw new Error('菜单商品缺少有效的 availableStock，请检查后端响应')
          }
          return { ...item, image: resolveImageUrl(item.image) }
        })
      }
    }
  }
}

module.exports = { createApiMenuSource }
