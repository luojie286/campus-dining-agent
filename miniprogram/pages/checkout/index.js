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
