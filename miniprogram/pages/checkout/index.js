const addresses = require('../../services/addresses')
const orders = require('../../services/orders')

Page({
  data: { fulfillment: 'DELIVERY', addresses: [], addressIndex: -1, remark: '',
    loading: false, busy: false, error: '', order: null, deadline: '' },

  onShow() {
    if (!this.data.order) this.loadAddresses()
  },

  async loadAddresses() {
    this.setData({ loading: true, error: '' })
    try {
      const old = this.data.addresses[this.data.addressIndex]
      const rows = (await addresses.list()).map(row => ({ ...row, label: row.building + ' ' + row.detail }))
      let index = old ? rows.findIndex(row => row.id === old.id) : -1
      if (index < 0) index = rows.findIndex(row => row.isDefault)
      if (index < 0 && rows.length) index = 0
      this.setData({ addresses: rows, addressIndex: index })
    } catch (error) {
      this.setData({ error: error.message || '收餐地址读取失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  chooseFulfillment(event) {
    if (this.data.busy) return
    this.setData({ fulfillment: event.currentTarget.dataset.type, error: '' })
  },

  chooseAddress(event) { this.setData({ addressIndex: Number(event.detail.value) }) },
  changeRemark(event) { this.setData({ remark: event.detail.value }) },

  payMock() {
    return this.changeOrder(order => orders.payMock(order.id))
  },

  confirmCancel() {
    if (this.data.busy || this.data.order?.status !== 'UNPAID') return
    wx.showModal({
      title: '取消未支付订单',
      content: '取消后将释放本次订单的预占库存。',
      success: result => {
        if (result.confirm) this.changeOrder(order => orders.cancel(order.id))
      }
    })
  },

  async changeOrder(action) {
    const order = this.data.order
    if (this.data.busy || !order || order.status !== 'UNPAID') return
    this.setData({ busy: true, error: '' })
    try {
      this.setData({ order: await action(order) })
    } catch (error) {
      this.setData({ error: error.message || '订单操作失败，请重试' })
      try {
        this.setData({ order: await orders.detail(order.id) })
      } catch {
        // 保留原操作错误，用户可以再次尝试。
      }
    } finally {
      this.setData({ busy: false })
    }
  },

  async submit() {
    if (this.data.busy || this.data.order || this.data.loading) return
    const { fulfillment, remark, addressIndex } = this.data
    const body = { fulfillment, remark: remark.trim() }
    if (fulfillment === 'DELIVERY') {
      const address = this.data.addresses[addressIndex]
      if (!address) {
        this.setData({ error: '请先选择收餐地址' })
        return
      }
      body.addressId = address.id
    }

    this.setData({ busy: true, error: '' })
    try {
      const order = await orders.submit(body)
      const deadline = order.paymentDeadline.replace('T', ' ').slice(0, 16)
      this.setData({ order, deadline })
    } catch (error) {
      this.setData({ error: error.message || '下单失败，请重试' })
    } finally {
      this.setData({ busy: false })
    }
  }
})
