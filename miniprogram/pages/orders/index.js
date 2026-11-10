const api = require('../../services/orders')
const { labels, displayOrder } = require('../../services/order-display')

const filters = [{ value: '', label: '全部状态' }, ...Object.entries(labels).map(
  ([value, label]) => ({ value, label })
)]
const PAGE_SIZE = 8

Page({
  data: { filters, selected: 0, records: [], page: 0, hasMore: false, loading: false, error: '' },

  onShow() { this.reload() },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() { this.loadMore() },

  selectStatus(event) {
    this.setData({ selected: Number(event.detail.value) })
    this.reload()
  },

  reload() {
    this.version = (this.version || 0) + 1
    this.setData({ records: [], page: 0, hasMore: false })
    return this.loadPage(1, this.version)
  },

  loadMore() {
    if (!this.data.loading && this.data.hasMore && !this.data.error) {
      return this.loadPage(this.data.page + 1, this.version)
    }
  },

  retry() {
    if (!this.data.loading) return this.loadPage(this.data.page + 1, this.version)
  },

  async loadPage(page, version) {
    const status = filters[this.data.selected].value
    const query = { page, pageSize: PAGE_SIZE }
    if (status) query.status = status
    this.setData({ loading: true, error: '' })
    try {
      const result = await api.page(query)
      if (version !== this.version) return
      const records = result.records.map(displayOrder)
      this.setData({
        records: page === 1 ? records : [...this.data.records, ...records],
        page,
        hasMore: records.length > 0 && page * PAGE_SIZE < result.total
      })
    } catch (error) {
      if (version === this.version) this.setData({ error: error.message || '订单加载失败' })
    } finally {
      if (version === this.version) this.setData({ loading: false })
    }
  }
})
