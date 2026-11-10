const api = require('../../services/addresses')

function blankAddress() {
  return { id: 0, consignee: '', phone: '', building: '', detail: '', isDefault: false }
}

Page({
  data: { addresses: [], draft: null, loading: false, busy: false, error: '', formError: '' },

  onShow() { this.load() },

  async load() {
    if (this.data.loading) return
    this.setData({ loading: true, error: '' })
    try {
      this.setData({ addresses: await api.list() })
    } catch (error) {
      this.setData({ error: error.message || '地址加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  createAddress() {
    if (!this.data.busy) this.setData({ draft: blankAddress(), formError: '' })
  },

  editAddress(event) {
    if (this.data.busy) return
    const id = Number(event.currentTarget.dataset.id)
    const address = this.data.addresses.find(item => item.id === id)
    if (address) this.setData({ draft: { ...address }, formError: '' })
  },

  closeForm() {
    if (!this.data.busy) this.setData({ draft: null, formError: '' })
  },

  changeField(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ ['draft.' + field]: event.detail.value })
  },

  changeDefault(event) {
    this.setData({ 'draft.isDefault': event.detail.value })
  },

  async save() {
    if (this.data.busy || !this.data.draft) return
    const { id, consignee, phone, building, detail, isDefault } = this.data.draft
    const address = { id, consignee: consignee.trim(), phone: phone.trim(),
      building: building.trim(), detail: detail.trim(), isDefault }
    if (!address.consignee || !address.building || !address.detail) {
      this.setData({ formError: '请填写收餐人、楼栋和详细位置' })
      return
    }
    if (!/^1[0-9]{10}$/.test(address.phone)) {
      this.setData({ formError: '请输入有效的11位手机号' })
      return
    }

    this.setData({ busy: true, formError: '' })
    try {
      await api.save(address)
      this.setData({ draft: null })
      await this.load()
    } catch (error) {
      this.setData({ formError: error.message || '保存地址失败' })
    } finally {
      this.setData({ busy: false })
    }
  },

  makeDefault(event) {
    const id = Number(event.currentTarget.dataset.id)
    return this.applyChange(() => api.setDefault(id))
  },

  confirmRemove(event) {
    if (this.data.busy) return
    const id = Number(event.currentTarget.dataset.id)
    wx.showModal({
      title: '删除收餐地址',
      content: '删除后需要重新添加才能使用。',
      success: result => { if (result.confirm) this.applyChange(() => api.remove(id)) }
    })
  },

  async applyChange(change) {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    try {
      await change()
      await this.load()
    } catch (error) {
      this.setData({ error: error.message || '地址更新失败' })
    } finally {
      this.setData({ busy: false })
    }
  }
})
