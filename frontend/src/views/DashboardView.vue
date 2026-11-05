<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { app, refreshShop, safely, updateShopStatus } from '@/stores/app'
import { money, dateTime, localDate, statusLabels, statusColors, type Dashboard, type Order } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import TrendChart from '@/components/TrendChart.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter(); const metrics = ref<Dashboard | null>(null); const orders = ref<Order[]>([]); const loading = ref(true); const error = ref('')
async function load() {
  error.value = ''
  try { const [m, o] = await Promise.all([api.metrics(), api.orders({ pageSize: 5, begin: localDate(), end: localDate() })]); metrics.value = m; orders.value = o.records }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败' }
  finally { loading.value = false }
}
const shopStatusText = computed(() => {
  if (app.shopSaving) return '正在更新营业状态…'
  if (app.shopLoading) return '正在读取营业状态…'
  if (!app.shop) return '营业状态未知'
  return app.shop.open ? '食堂营业中' : '食堂休息中'
})

async function toggleShop(): Promise<void> {
  if (!app.shop || app.shopLoading || app.shopSaving) return
  const targetOpen = !app.shop.open
  await safely(() => updateShopStatus(targetOpen), '营业状态已更新')
}
watch(() => app.revision, () => { void load() })
onMounted(() => {
  void refreshShop()
  void load()
})
</script>
<template>
  <div class="welcome-banner"><div><h2>{{ app.session?.name }}，今天也辛苦了。</h2><p>从一份午餐开始，把园区的每一天照顾好。</p></div><Icon class="banner-mark" name="sprout" :size="105"/><button class="banner-status" :disabled="app.shopLoading || app.shopSaving || !app.shop" @click="toggleShop"><span class="dot" :style="{ background: app.shop?.open ? '#73985d' : '#b0b39f' }"></span>{{ shopStatusText }}<Icon name="down" :size="13"/></button></div>
  <div v-if="app.shopError" class="inline-error" role="alert">
    {{ app.shopError }}
    <button class="btn text" @click="refreshShop">重试营业状态</button>
  </div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/><span>{{ error }}</span><button class="btn text" @click="load">重试</button></div>
  <div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>正在准备工作台…</div>
  <template v-if="metrics">
    <div class="metric-grid"><div class="metric-card highlight"><div class="metric-top"><span>近 7 天收入</span><span class="metric-icon"><Icon name="reports" :size="17"/></span></div><div class="metric-value"><small>¥</small>{{ money(metrics.revenue) }}</div><div class="metric-note"><span class="positive">按支付时间统计</span><span>不含模拟退款</span></div></div><div class="metric-card"><div class="metric-top"><span>已支付订单</span><span class="metric-icon"><Icon name="orders" :size="17"/></span></div><div class="metric-value">{{ metrics.paidOrders }}<small style="margin-left:5px;font-size:12px;color:#8f9b80">笔</small></div><div class="metric-note">近 7 天支付成功的订单</div></div><div class="metric-card"><div class="metric-top"><span>平均实付</span><span class="metric-icon"><Icon name="payment" :size="17"/></span></div><div class="metric-value"><small>¥</small>{{ money(metrics.averageAmount) }}</div><div class="metric-note">收入 / 已支付订单数</div></div><div class="metric-card"><div class="metric-top"><span>已完成订单</span><span class="metric-icon"><Icon name="success" :size="17"/></span></div><div class="metric-value">{{ metrics.completedOrders }}<small style="margin-left:5px;font-size:12px;color:#8f9b80">笔</small></div><div class="metric-note">按订单创建日期筛选</div></div></div>
    <div class="grid-two"><div class="stack"><section class="panel"><div class="panel-head"><div><h2>收入趋势</h2><p>{{ metrics.period.begin }} — {{ metrics.period.end }}</p></div><span class="chart-legend"><span class="dot"></span>收入（元）</span></div><TrendChart :values="metrics.trend"/></section><section class="panel"><div class="panel-head"><h2>订单待办</h2><RouterLink to="/admin/orders" class="text-link">查看全部<Icon name="right" :size="13"/></RouterLink></div><div class="order-shortcut-grid"><button class="order-shortcut" @click="router.push('/admin/orders?status=UNPAID')"><span class="shortcut-icon"><Icon name="clock"/></span><strong>{{ metrics.counts.UNPAID }}</strong><small>待支付</small></button><button class="order-shortcut" @click="router.push('/admin/orders?status=PREPARING')"><span class="shortcut-icon"><Icon name="dish"/></span><strong>{{ metrics.counts.PREPARING }}</strong><small>制作中</small></button><button class="order-shortcut" @click="router.push('/admin/orders?status=READY_DELIVERY')"><span class="shortcut-icon"><Icon name="truck"/></span><strong>{{ metrics.counts.READY_DELIVERY }}</strong><small>待配送</small></button><button class="order-shortcut" @click="router.push('/admin/orders?status=READY_PICKUP')"><span class="shortcut-icon"><Icon name="cart"/></span><strong>{{ metrics.counts.READY_PICKUP }}</strong><small>待自提</small></button></div><p class="metric-detail">待办数量为近 7 天创建的订单；订单管理可查看全部记录。</p></section></div><div class="stack"><section class="panel"><div class="panel-head"><div><h2>人气菜单</h2><p>近 7 天销量排行</p></div><RouterLink to="/admin/reports" class="text-link"><Icon name="upRight" :size="17"/></RouterLink></div><div class="ranking-list"><div v-for="(item, i) in metrics.sales.slice(0, 5)" :key="item.id" class="ranking-item"><span class="rank-number" :class="{ top: i < 3 }">{{ i + 1 }}</span><div class="ranking-main"><div class="ranking-name">{{ item.name }}<span>{{ item.quantity }} 份</span></div><div class="rank-track"><i :style="{ width: (item.quantity / (metrics.sales[0]?.quantity || 1) * 100) + '%' }"></i></div></div></div><EmptyState v-if="!metrics.sales.length" title="暂无销量" description="支付订单产生后可统计。"/></div></section><section class="agent-card"><h3><Icon name="agent" :size="19"/>你的运营助手<span class="tiny-label">AGENT</span></h3><p>查营业额、看销量，或准备一项运营操作。<br>让数据为你的下一步提供依据。</p><RouterLink to="/admin/agent" class="btn lime">与助手聊聊<Icon name="arrow" :size="14"/></RouterLink><Icon name="agent" class="agent-symbol" :size="65"/></section><div class="quick-link-row"><RouterLink to="/admin/dishes" class="quick-link"><Icon name="plus" :size="18"/>维护菜品</RouterLink><RouterLink to="/admin/setmeals" class="quick-link"><Icon name="setmeal" :size="18"/>组合套餐</RouterLink><RouterLink to="/admin/reports" class="quick-link"><Icon name="reports" :size="18"/>经营统计</RouterLink></div></div></div>
  </template>
  <section class="panel table-panel section-gap"><div class="panel-head"><div><h2>今日订单</h2><p>最新的园区餐饮动态</p></div><RouterLink to="/admin/orders" class="text-link">订单管理<Icon name="right" :size="13"/></RouterLink></div><div class="table-scroll"><table v-if="orders.length"><thead><tr><th>订单号 / 时间</th><th>员工</th><th>订单内容</th><th>金额</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="order in orders" :key="order.id"><td class="order-id">{{ order.number }}<span class="table-sub">{{ dateTime(order.createdAt) }}</span></td><td>{{ order.userName }}</td><td>{{ order.lines[0]?.name }}<span v-if="order.lines.length > 1"> 等 {{ order.lines.length }} 项</span></td><td class="strong">¥{{ money(order.amount) }}</td><td><span class="badge" :class="statusColors[order.status]">{{ statusLabels[order.status] }}</span></td><td><button class="btn text" @click="router.push('/admin/orders?detail=' + order.id)">查看<Icon name="right" :size="12"/></button></td></tr></tbody></table><EmptyState v-else title="今天还没有订单" description="员工下单后，新订单会出现在这里。" icon="orders"/></div></section>
</template>
