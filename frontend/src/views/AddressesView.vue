<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { app, safely } from '@/stores/app'
import type { Address } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import EmptyState from '@/components/EmptyState.vue'
import Modal from '@/components/Modal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { cloneRecord } from '@/utils/clone'

const addresses = ref<Address[]>([]); const form = ref<Address | null>(null); const deleting = ref<Address | null>(null); const busy = ref(false); const error = ref(''); const loading = ref(true)
async function load() { error.value = ''; try { addresses.value = await api.addresses() } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false } }
function edit(address?: Address) { form.value = address ? cloneRecord(address) : { id: 0, userId: app.session!.id, consignee: app.session!.name, phone: '', building: '', detail: '', isDefault: !addresses.value.length } }
async function save() { if (!form.value || busy.value) return; busy.value = true; await safely(async () => { await api.saveAddress(form.value!); form.value = null; await load() }, '地址已保存'); busy.value = false }
async function remove() { if (!deleting.value) return; busy.value = true; await safely(async () => { await api.deleteAddress(deleting.value!.id); deleting.value = null; await load() }, '地址已删除'); busy.value = false }
async function setDefault(address: Address) { await safely(async () => { await api.saveAddress({ ...address, isDefault: true }); await load() }, '默认地址已更新') }
onMounted(load)
</script>
<template>
  <div class="toolbar"><span class="muted" style="font-size:12px">共 {{ addresses.length }} 个收餐地址</span><button class="btn primary" @click="edit()"><Icon name="plus" :size="17"/>新增地址</button></div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="16"/>{{ error }}<button class="btn text" @click="load">重试</button></div><div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>加载地址…</div>
  <div v-else-if="addresses.length" class="address-grid"><article v-for="address in addresses" :key="address.id" class="address-card" :class="{ default: address.isDefault }"><div class="address-card-top"><span class="address-avatar"><Icon name="address" :size="19"/></span><strong>{{ address.building }}</strong><span v-if="address.isDefault" class="badge green">默认地址</span></div><p>{{ address.detail }}</p><p>{{ address.consignee }}<span style="margin-left:17px">{{ address.phone }}</span></p><div class="address-card-footer"><button class="btn text" :disabled="address.isDefault" @click="setDefault(address)">{{ address.isDefault ? '下单时优先使用' : '设为默认' }}</button><div class="row-actions"><button class="icon-btn" :aria-label="'编辑' + address.building" @click="edit(address)"><Icon name="edit" :size="16"/></button><button class="icon-btn danger" :aria-label="'删除' + address.building" @click="deleting = address"><Icon name="delete" :size="16"/></button></div></div></article></div><section v-else class="panel"><EmptyState title="还没有收餐地址" description="添加办公楼与具体位置，配送时就能送到你身边。" icon="address"><button class="btn primary" @click="edit()">添加第一个地址</button></EmptyState></section>
  <Modal v-if="form" :title="form.id ? '编辑收餐地址' : '新增收餐地址'" :busy="busy" @close="!busy && (form = null)"><div class="field-grid"><label class="field"><span>收餐人</span><input v-model="form.consignee" maxlength="30"/></label><label class="field"><span>联系电话</span><input v-model="form.phone" type="tel" maxlength="11"/></label></div><label class="field"><span>楼栋 / 地点</span><input v-model="form.building" placeholder="例如：A 座" maxlength="50"/></label><label class="field"><span>详细地址</span><input v-model="form.detail" placeholder="例如：8 楼 · 产品研发部" maxlength="100"/></label><label class="field checkbox"><input v-model="form.isDefault" type="checkbox"/><span>设为默认收餐地址</span></label><template #footer><button class="btn" :disabled="busy" @click="form = null">取消</button><button class="btn primary" :disabled="busy" @click="save">保存地址</button></template></Modal>
  <ConfirmDialog v-if="deleting" title="删除收餐地址" :message="`确定删除 ${deleting.building} · ${deleting.detail}？历史订单地址快照保持不变。`" danger :busy="busy" @close="!busy && (deleting = null)" @confirm="remove"/>
</template>
