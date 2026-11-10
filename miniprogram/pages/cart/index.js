const cart = require('../../services/cart')
const { createMenuSource } = require('../../services/menu')

Page({
  data: { rows: [], loading: true, busy: false, error: '', catalogWarning: '' },

  onShow() { this.load() },

  async load() {
    this.setData({ loading: true, error: '', catalogWarning: '' })
    try {
      const lines = await cart.list()
      // 购物车接口只有ID和数量，显示价从当前菜单查，找不到的商品仍能移除。
      const items = new Map()
      let catalogWarning = ''
      if (lines.length) {
        try {
          const source = createMenuSource()
          for (let page = 1; page <= 100; page++) {
            const result = await source.getMenuPage({ page, pageSize: 100 })
            for (const item of result.records) items.set(item.id, item)
            if (lines.every(line => items.has(line.itemId)) ||
                !result.records.length || page * 100 >= result.total) break
            if (page === 100) catalogWarning = '菜单记录较多，部分商品资料未加载。'
          }
        } catch (error) {
          catalogWarning = error.message || '商品资料加载失败'
        }
      }
      const rows = lines.map(line => {
        const item = items.get(line.itemId)
        return {
          ...line,
          name: item ? item.name : '商品 #' + line.itemId,
          subtotal: item ? (item.price * line.quantity).toFixed(2) : '',
          unavailable: item ? item.availableStock < line.quantity : !catalogWarning
        }
      })
      this.setData({ rows, catalogWarning })
    } catch (error) {
      this.setData({ error: error.message || '购物车读取失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async remove(event) {
    if (this.data.busy || this.data.loading) return
    const itemId = Number(event.currentTarget.dataset.id)
    this.setData({ busy: true, error: '' })
    try {
      await cart.remove(itemId)
      this.setData({ rows: this.data.rows.filter(row => row.itemId !== itemId) })
      await this.load()
    } catch (error) {
      this.setData({ error: error.message || '移除失败' })
    } finally {
      this.setData({ busy: false })
    }
  },

  confirmClear() {
    if (this.data.busy || this.data.loading || !this.data.rows.length) return
    wx.showModal({
      title: '清空购物车',
      content: '将移除购物车中的所有商品。',
      success: result => { if (result.confirm) this.clear() }
    })
  },

  async clear() {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    try {
      await cart.clear()
      this.setData({ rows: [], catalogWarning: '' })
    } catch (error) {
      this.setData({ error: error.message || '清空失败' })
    } finally {
      this.setData({ busy: false })
    }
  }
})
