const { createMenuSource, scenes } = require('../../services/menu')
const config = require('../../services/config')

const cart = require('../../services/cart')

const PAGE_SIZE = 6
const ALL_CATEGORY = { id: 0, name: '全部商品' }
const FOOD_ART = ['chicken', 'fish', 'beef', 'salad', 'noodle']

function toProductCard(item) {
  const art = FOOD_ART.includes(item.art) ? item.art : 'chicken'
  const fallback = '/assets/food/' + art + '.png'
  return {
    ...item,
    priceText: Number(item.price).toFixed(2),
    soldOut: item.availableStock <= 0,
    imageSrc: item.image || fallback,
    fallback
  }
}

Component({
  data: {
    shop: null,
    categories: [ALL_CATEGORY],
    categoryId: 0,
    categoryName: ALL_CATEGORY.name,
    products: [],
    total: 0,
    page: 0,
    hasMore: false,
    loading: true,
    loadingMore: false,
    refreshing: false,
    error: '',
    detail: null,
    adding: false,
    scrollTarget: '',
    scenes,
    sceneIndex: 0,
    isDemo: config.mode === 'mock'
  },

  lifetimes: {
    attached() {
      this.queryVersion = 0
      this.source = createMenuSource()
      this.loadStore(0)
    },
    detached() {
      // 页面销毁后忽略还没返回的请求。
      this.queryVersion++
    }
  },

  methods: {
    openCart() {
      if (this.data.adding) return
      wx.navigateTo({ url: '/pages/cart/index' })
    },

    async addToCart(event) {
      if (this.data.adding) return
      const itemId = Number(event.currentTarget.dataset.id)
      const item = this.data.products.find(product => product.id === itemId)
      if (!item || item.soldOut) return
      this.setData({ adding: true })
      try {
        await cart.add(itemId)
        wx.showToast({ title: '已加入购物车', icon: 'success' })
      } catch (error) {
        wx.showToast({ title: error.message || '加购失败，请重试', icon: 'none' })
      } finally {
        this.setData({ adding: false })
      }
    },

    async loadStore(categoryId, refreshing = false) {
      const version = this.startQuery(refreshing)
      this.setData({ shop: null, categories: [ALL_CATEGORY] })

      try {
        const [shop, categories] = await Promise.all([
          this.source.getShop(),
          this.source.getCategories()
        ])
        if (version !== this.queryVersion) return

        const selected = categories.find(category => category.id === categoryId) || ALL_CATEGORY
        this.setData({
          shop,
          categories: [ALL_CATEGORY, ...categories],
          categoryId: selected.id,
          categoryName: selected.name
        })
        await this.fetchPage(1, version)
      } catch (error) {
        if (version !== this.queryVersion) return
        this.setData({ error: error.message || '食堂信息加载失败', loading: false, refreshing: false })
      }
    },

    startQuery(refreshing = false) {
      // 切换分类或场景时废弃旧请求，慢响应不能覆盖新列表。
      this.queryVersion++
      this.setData({
        products: [], total: 0, page: 0, hasMore: false,
        loading: true, loadingMore: false, refreshing, error: '', detail: null,
        scrollTarget: ''
      })
      return this.queryVersion
    },

    async fetchPage(page, version) {
      const categoryId = this.data.categoryId
      const query = { page, pageSize: PAGE_SIZE }
      if (categoryId !== 0) query.categoryId = categoryId

      try {
        const result = await this.source.getMenuPage(query)
        if (version !== this.queryVersion) return

        const records = result.records.map(toProductCard)
        const products = page === 1 ? records : [...this.data.products, ...records]
        this.setData({
          products,
          total: result.total,
          page,
          hasMore: records.length > 0 && page * PAGE_SIZE < result.total
        })
      } catch (error) {
        if (version !== this.queryVersion) return
        this.setData({ error: error.message || '菜单加载失败，请重试' })
      } finally {
        if (version === this.queryVersion) {
          this.setData({ loading: false, loadingMore: false, refreshing: false })
        }
      }
    },

    selectCategory(event) {
      const category = this.data.categories.find(item => item.id === Number(event.currentTarget.dataset.id))
      if (!category || !this.data.shop || category.id === this.data.categoryId) return
      const version = this.startQuery()
      this.setData({
        categoryId: category.id,
        categoryName: category.name,
        scrollTarget: 'menu-top'
      })
      this.fetchPage(1, version)
    },

    loadMore() {
      if (this.data.loading || this.data.loadingMore || this.data.error || !this.data.hasMore) return
      this.setData({ loadingMore: true, scrollTarget: '' })
      this.fetchPage(this.data.page + 1, this.queryVersion)
    },

    retry() {
      if (this.data.loading || this.data.loadingMore) return
      if (!this.data.shop) {
        this.loadStore(this.data.categoryId)
        return
      }
      if (this.data.page === 0) {
        const version = this.startQuery()
        this.fetchPage(1, version)
        return
      }

      this.setData({ error: '', loadingMore: true })
      this.fetchPage(this.data.page + 1, this.queryVersion)
    },

    refresh() {
      if (this.data.loading || this.data.refreshing) return
      this.loadStore(this.data.categoryId, true)
    },

    changeScene(event) {
      if (!this.data.isDemo) return
      const sceneIndex = Number(event.detail.value)
      if (!scenes[sceneIndex] || sceneIndex === this.data.sceneIndex) return
      this.source = createMenuSource(scenes[sceneIndex].value)
      this.setData({ sceneIndex })
      this.loadStore(0)
    },

    openDish(event) {
      const detail = this.data.products.find(item => item.id === Number(event.currentTarget.dataset.id))
      if (detail) this.setData({ detail })
    },

    closeDish() {
      this.setData({ detail: null })
    },

    imageFailed(event) {
      const id = Number(event.currentTarget.dataset.id)
      const index = this.data.products.findIndex(item => item.id === id && item.imageSrc !== item.fallback)
      if (index < 0) return
      const fallback = this.data.products[index].fallback
      const patch = {}
      patch['products[' + index + '].imageSrc'] = fallback
      if (this.data.detail && this.data.detail.id === id) patch['detail.imageSrc'] = fallback
      this.setData(patch)
    }
  }
})
