const api = require('../../services/orders')
const { displayOrder } = require('../../services/order-display')

Page({
  data: { order: null, loading: false, busy: false, error: '' },

  onLoad(query) {
    this.id = Number(query.id)
    this.load()
  },

  async load() {
    if (this.data.loading) return
    this.setData({ loading: true, error: '' })
    try {
      this.setData({ order: displayOrder(await api.detail(this.id)) })
    } catch (error) {
      this.setData({ error: error.message || '订单详情加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  pay() {
    return this.perform(async () => {
      this.setData({ order: displayOrder(await api.payMock(this.id)) })
    })
  },

  cancel() {
    if (this.data.busy) return
    wx.showModal({
      title: '取消订单',
      content: '取消后将释放本次预占的库存。',
      success: result => {
        if (result.confirm) this.perform(async () => {
          this.setData({ order: displayOrder(await api.cancel(this.id)) })
        })
      }
    })
  },

  reorder() {
    return this.perform(async () => {
      await api.reorder(this.id)
      wx.navigateTo({ url: '/pages/cart/index' })
    })
  },

  async perform(action) {
    if (this.data.busy || this.data.loading) return
    this.setData({ busy: true, error: '' })
    try {
      await action()
    } catch (error) {
      this.setData({ error: error.message || '操作失败，请重试' })
    } finally {
      this.setData({ busy: false })
    }
  }
})
