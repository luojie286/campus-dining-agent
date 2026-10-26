<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { notifications } from '@/api/notifications'
import { app, safely } from '@/stores/app'
import { dateTime, money, statusColors, statusLabels, type Order, type OrderStatus } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import EmptyState from '@/components/EmptyState.vue'
import Modal from '@/components/Modal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute(); const router = useRouter(); const admin = computed(() => app.session?.role === 'admin')
const status = ref(String(route.query.status || '')); const search = ref(''); const begin = ref(''); const end = ref(''); const records = ref<Order[]>([]); const page = ref(1); const total = ref(0); const detail = ref<Order | null>(null); const loading = ref(true); const error = ref(''); const action = ref<{ order: Order; type: 'pay' | 'cancel' | 'ready' | 'deliver' | 'complete' } | null>(null); const busy = ref(false); let loadId = 0; let timer: ReturnType<typeof setInterval> | undefined
const actionLabels = { pay: API_MODE === 'mock' ? '模拟支付' : '提交支付请求', cancel: '取消订单', ready: '完成备餐', deliver: '开始配送', complete: '完成订单' }
const tabs = computed(() => ['', 'UNPAID', 'PREPARING', 'READY_DELIVERY', 'DELIVERING', 'READY_PICKUP', 'COMPLETED', 'CANCELLED'] as const)
async function load(reset = false) {
  if (reset) page.value = 1; const current = ++loadId; loading.value = true; error.value = ''
  try { const response = await api.orders({ status: status.value, name: search.value.trim(), begin: begin.value, end: end.value, page: page.value, pageSize: 8 }); if (current !== loadId) return; records.value = response.records; total.value = response.total; notifications.unread = 0 }
  catch (e) { if (current === loadId) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (current === loadId) loading.value = false }
}
async function show(id: number) { await safely(async () => { detail.value = await api.order(id) }) }
function closeDetail() { detail.value = null; if (route.query.detail) { const query = { ...route.query }; delete query.detail; router.replace({ query }) } }
async function execute() {
  if (!action.value || busy.value) return; busy.value = true
  await safely(async () => { const result = await api.orderAction(action.value!.order.id, action.value!.type); if (detail.value?.id === result.id) detail.value = result; action.value = null; await load() }, '订单操作已返回，请查看最新状态')
  busy.value = false
}
function allowed(order: Order): ('pay' | 'cancel' | 'ready' | 'deliver' | 'complete')[] {
  if (!admin.value) return order.status === 'UNPAID' ? ['pay', 'cancel'] : []
  const list: ('pay' | 'cancel' | 'ready' | 'deliver' | 'complete')[] = []
  if (order.status === 'PREPARING') list.push('ready')
  if (order.status === 'READY_DELIVERY') list.push('deliver')
  if (order.status === 'DELIVERING' || order.status === 'READY_PICKUP') list.push('complete')
  if (['UNPAID', 'PREPARING', 'READY_DELIVERY', 'READY_PICKUP'].includes(order.status)) list.push('cancel')
  return list
}
watch(status, () => { void load(true) })
watch(() => route.query.status, value => { status.value = String(value || '') })
watch(() => route.query.detail, id => { if (id && Number.isFinite(Number(id))) void show(Number(id)) })
watch(() => app.revision, () => { void load(); if (detail.value) void show(detail.value.id) })
onMounted(async () => { await load(); if (route.query.detail) await show(Number(route.query.detail)); if (API_MODE === 'mock') timer = setInterval(() => { void load(); if (detail.value) void show(detail.value.id) }, 30000) })
onBeforeUnmount(() => clearInterval(timer))
</script>
<template>
  <div class="toolbar"><form class="filters" @submit.prevent="load(true)"><div class="search-field"><Icon name="search" :size="16"/><input v-model="search" :placeholder="admin ? '搜索订单号或员工姓名' : '搜索订单号'" aria-label="搜索订单"/></div><input v-model="begin" type="date" aria-label="订单开始日期"/><span class="muted">—</span><input v-model="end" type="date" aria-label="订单结束日期"/><button class="btn" type="submit">查询</button></form><button class="btn" @click="load()"><Icon name="refresh" :size="15"/>刷新订单</button></div>
  <div class="filter-tabs"><button v-for="tab in tabs" :key="tab" :class="{ active: status === tab }" @click="status = tab">{{ tab ? statusLabels[tab as OrderStatus] : '全部订单' }}</button></div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/>{{ error }}<button class="btn text" @click="load()">重试</button></div>
  <section class="panel table-panel"><div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>加载订单…</div><div v-else class="table-scroll"><table v-if="records.length"><thead><tr><th>订单号 / 时间</th><th>{{ admin ? '员工 / 收餐方式' : '商品 / 收餐方式' }}</th><th>实付 / 应付</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="order in records" :key="order.id"><td class="order-id">{{ order.number }}<span class="table-sub">{{ dateTime(order.createdAt) }}</span></td><td>{{ admin ? order.userName : order.lines[0]?.name }}<span class="table-sub">{{ order.fulfillment === 'PICKUP' ? '到店自提' : '园区配送' }}{{ !admin && order.lines.length > 1 ? ' · 共 ' + order.lines.length + ' 项' : '' }}</span></td><td class="strong">¥{{ money(order.amount) }}<span class="table-sub">{{ order.payStatus === 'PAID' ? '已支付' : order.payStatus === 'REFUNDED' ? (API_MODE === 'mock' ? '模拟已退款' : '已退款') : '未支付' }}</span></td><td><span class="badge" :class="statusColors[order.status]">{{ statusLabels[order.status] }}</span></td><td><div class="row-actions"><button class="btn text" @click="show(order.id)">详情</button><button v-for="operation in allowed(order)" :key="operation" class="btn text" :style="operation === 'cancel' ? { color: '#ac8670' } : {}" @click="action = { order, type: operation }">{{ actionLabels[operation] }}</button></div></td></tr></tbody></table><EmptyState v-else title="没有找到订单" :description="admin ? '调整日期或状态筛选条件。' : '去今日菜单选一顿喜欢的午餐吧。'" icon="orders"><RouterLink v-if="!admin" to="/user/menu" class="btn primary">去点餐</RouterLink></EmptyState></div><Pagination :page="page" :total="total" @change="p => { page = p; load() }"/></section>
  <div v-if="API_MODE === 'mock'" class="inline-note section-gap"><Icon name="eye" :size="16"/>当前为演示订单。支付、取消退款与超时关闭均在前端本地模拟，不涉及真实交易；真实状态、退款与定时任务由你的后端实现。</div>
  <Modal v-if="detail" title="订单详情" wide @close="closeDetail"><div style="display:flex;align-items:center;justify-content:space-between;gap:10px"><strong class="order-id">{{ detail.number }}</strong><span class="badge" :class="statusColors[detail.status]">{{ statusLabels[detail.status] }}</span></div><div class="order-detail-grid"><div><span>下单员工</span>{{ detail.userName }}</div><div><span>下单时间</span>{{ dateTime(detail.createdAt) }}</div><div><span>收餐方式</span>{{ detail.fulfillment === 'PICKUP' ? '到店自提' : '园区配送' }}</div><div><span>收餐人</span>{{ detail.recipient }} {{ detail.phone }}</div><div style="grid-column:1/-1"><span>收餐地点</span>{{ detail.address }}</div><div v-if="detail.remark" style="grid-column:1/-1"><span>备注</span>{{ detail.remark }}</div><div v-if="detail.status === 'UNPAID'"><span>支付截止</span>{{ dateTime(detail.paymentDeadline) }}</div></div><div class="table-scroll"><table><thead><tr><th>订单商品快照</th><th>单价</th><th>数量</th><th>小计</th></tr></thead><tbody><tr v-for="line in detail.lines" :key="line.itemId"><td>{{ line.name }}</td><td>¥{{ money(line.price) }}</td><td>×{{ line.quantity }}</td><td>¥{{ money(line.price * line.quantity) }}</td></tr></tbody></table></div><div class="summary-row total"><span>订单金额</span><strong>¥{{ money(detail.amount) }}</strong></div><h3>订单进度</h3><div class="timeline"><div v-for="(event, i) in detail.events" :key="i" class="timeline-entry"><strong>{{ statusLabels[event.status] }}</strong><small>{{ dateTime(event.time) }} · {{ event.note }}</small></div></div><template #footer><button v-for="operation in allowed(detail)" :key="operation" class="btn" :class="operation === 'cancel' ? 'danger' : 'primary'" @click="action = { order: detail!, type: operation }">{{ actionLabels[operation] }}</button><button class="btn" @click="closeDetail">关闭</button></template></Modal>
  <ConfirmDialog v-if="action" :title="actionLabels[action.type]" :message="`订单：${action.order.number}\n当前状态：${statusLabels[action.order.status]}\n${action.type === 'pay' ? (API_MODE === 'mock' ? '将模拟支付 ¥' + money(action.order.amount) + '，不会真实扣款。' : '向后端提交支付请求，支付是否成功以服务端验证结果为准。') : action.type === 'cancel' ? '请确认取消，库存与支付处理按照后端规则执行。演示模式模拟释放库存与退款。' : '请根据实际备餐或履约情况确认更新状态。'}`" :danger="action.type === 'cancel'" :busy="busy" @close="!busy && (action = null)" @confirm="execute"/>
</template>
