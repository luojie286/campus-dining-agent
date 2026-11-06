<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { money, localDate, type Dashboard } from '@/domain/types'
import { downloadCsv } from '@/utils/export'
import Icon from '@/components/Icon.vue'
import TrendChart from '@/components/TrendChart.vue'
import EmptyState from '@/components/EmptyState.vue'

const begin = ref(''); const end = ref(localDate()); const data = ref<Dashboard | null>(null); const loading = ref(false); const error = ref(''); const metric = ref<'revenue' | 'orders'>('revenue')
function range(days: number) { const d = new Date(); d.setDate(d.getDate() - days + 1); begin.value = localDate(d); end.value = localDate(); void load() }
async function load() { loading.value = true; error.value = ''; try { data.value = await api.metrics(begin.value, end.value) } catch (e) { error.value = e instanceof Error ? e.message : '统计加载失败' } finally { loading.value = false } }
function exportReport() {
  if (!data.value) return
  const d = data.value
  downloadCsv(`园味经营统计_${d.period.begin}_${d.period.end}.csv`, [
    ['统计范围', d.period.begin, d.period.end], ['统计口径', d.metricNote], [],
    ['日期', '收入（元）', '创建订单（笔）'], ...d.trend.map(x => [x.date, x.revenue.toFixed(2), x.orders]), [],
    ['商品', '销量（份）', '销售额（元）'], ...d.sales.map(x => [x.name, x.quantity, x.amount.toFixed(2)])
  ])
}
onMounted(() => range(7))
</script>
<template>
  <form class="report-filter" @submit.prevent="load"><input v-model="begin" type="date" required aria-label="统计开始日期"/><span>至</span><input v-model="end" type="date" required aria-label="统计结束日期"/><button class="btn primary" :disabled="loading" type="submit">查询统计</button><div class="segmented"><button type="button" @click="range(1)">今天</button><button type="button" @click="range(7)">近 7 天</button><button type="button" @click="range(30)">近 30 天</button></div><button class="btn" type="button" :disabled="!data || loading" @click="exportReport" style="margin-left:auto"><Icon name="download" :size="15"/>导出 CSV</button></form>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/>{{ error }}</div><div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>正在统计…</div>
  <template v-else-if="data"><div class="metric-grid"><div class="metric-card highlight"><div class="metric-top"><span>统计收入</span><span class="metric-icon"><Icon name="reports" :size="17"/></span></div><div class="metric-value"><small>¥</small>{{ money(data.revenue) }}</div><div class="metric-note">支付时间范围内，当前未退款金额</div></div><div class="metric-card"><div class="metric-top"><span>创建订单</span><span class="metric-icon"><Icon name="orders" :size="17"/></span></div><div class="metric-value">{{ data.orderCount }}</div><div class="metric-note">其中已支付 {{ data.paidOrders }} 笔（按支付时间）</div></div><div class="metric-card"><div class="metric-top"><span>平均实付</span><span class="metric-icon"><Icon name="payment" :size="17"/></span></div><div class="metric-value"><small>¥</small>{{ money(data.averageAmount) }}</div><div class="metric-note">收入 / 已支付订单数</div></div><div class="metric-card"><div class="metric-top"><span>取消订单占比</span><span class="metric-icon"><Icon name="orders" :size="17"/></span></div><div class="metric-value">{{ data.cancellationRate.toFixed(1) }}<small>%</small></div><div class="metric-note">按订单创建日期统计</div></div></div><section class="panel"><div class="panel-head"><div><h2>{{ metric === 'revenue' ? '收入趋势' : '订单趋势' }}</h2><p>{{ data.period.begin }} — {{ data.period.end }}</p></div><div class="segmented"><button :class="{ active: metric === 'revenue' }" @click="metric = 'revenue'">收入</button><button :class="{ active: metric === 'orders' }" @click="metric = 'orders'">订单</button></div></div><TrendChart :values="data.trend" :metric="metric"/></section><div class="grid-two section-gap"><section class="panel table-panel"><div class="panel-head"><div><h2>商品销量排行</h2><p>按订单商品快照聚合，套餐与菜品分别计数</p></div><Icon name="dish" :size="18" style="color:var(--muted)"/></div><div class="table-scroll"><table v-if="data.sales.length"><thead><tr><th>排名</th><th>商品名称</th><th>销量</th><th>销售额</th></tr></thead><tbody><tr v-for="(item, index) in data.sales" :key="item.id"><td>{{ String(index + 1).padStart(2, '0') }}</td><td class="strong">{{ item.name }}</td><td>{{ item.quantity }} 份</td><td>¥{{ money(item.amount) }}</td></tr></tbody></table><EmptyState v-else title="该时段暂无销量" description="试试其他日期范围。" icon="reports"/></div></section><section class="panel"><div class="panel-head"><h2>订单概览</h2></div><div class="stats-mini-grid"><div class="stats-mini"><strong>{{ data.counts.COMPLETED }}</strong><small>已完成</small></div><div class="stats-mini"><strong>{{ data.counts.PREPARING + data.counts.READY_DELIVERY + data.counts.READY_PICKUP + data.counts.DELIVERING }}</strong><small>履约中</small></div><div class="stats-mini"><strong>{{ data.counts.CANCELLED }}</strong><small>已取消</small></div></div><div class="inline-note section-gap"><Icon name="help" :size="17"/><span>{{ data.metricNote }}</span></div><p class="metric-detail">订单创建与支付可能发生在不同日期，因此创建订单数与支付订单数不一定相等。</p></section></div><div class="report-agent-button"><div><h3>让运营助手帮你读懂数据</h3><p>基于结构化指标，生成经营观察和建议。</p></div><RouterLink to="/admin/agent?prompt=分析最近一周的营业额和菜品销量" class="btn primary"><Icon name="agent" :size="16"/>打开 Agent</RouterLink></div></template>
</template>
