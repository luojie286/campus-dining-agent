<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { money, localDate, statusLabels, type Dashboard, type OrderReport, type OrderStatus, type RevenueReport, type SalesReport, type UserReport } from '@/domain/types'
import { downloadCsv } from '@/utils/export'
import Icon from '@/components/Icon.vue'
import EChart from '@/components/EChart.vue'
import EmptyState from '@/components/EmptyState.vue'

const begin = ref('')
const end = ref(localDate())
const loading = ref(false)
const error = ref('')
const exporting = ref(false)
const notice = ref('')

const metrics = ref<Dashboard | null>(null)        // 旧 CSV 导出沿用概览口径
const revenue = ref<RevenueReport | null>(null)
const users = ref<UserReport | null>(null)
const orders = ref<OrderReport | null>(null)
const sales = ref<SalesReport | null>(null)

// 与浅色 UI 协调：文字深色可读，分割线浅色，配色沿用 EChart 的品牌蓝主色系。
const axisLabel = { color: '#5d7287', fontSize: 11 }
const axisLine = { lineStyle: { color: '#c9d9e6' } }
const splitLine = { lineStyle: { color: '#e8f0f6' } }
const legendText = { color: '#193247' }
// EChartsOption 是大型联合类型；统一构造入口让本页的 option 保持可读。
function chartOption(option: Record<string, unknown>): EChartsOption { return option as EChartsOption }
function categoryAxis(dates: string[]) {
  return { type: 'category', data: dates, axisLabel, axisLine, axisTick: { show: false } }
}
function valueAxis() {
  return { type: 'value', axisLabel, splitLine, axisLine: { show: false } }
}

const revenueOption = computed<EChartsOption>(() => {
  const trend = revenue.value?.trend || []
  return chartOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['实付金额', '退款金额', '净现金流入'], textStyle: legendText, top: 6 },
    grid: { left: 12, right: 18, top: 48, bottom: 12, containLabel: true },
    xAxis: categoryAxis(trend.map(point => point.date.slice(5))),
    yAxis: valueAxis(),
    series: [
      { name: '实付金额', type: 'bar', data: trend.map(point => point.grossPaidAmount) },
      { name: '退款金额', type: 'bar', data: trend.map(point => point.refundAmount) },
      { name: '净现金流入', type: 'line', smooth: true, data: trend.map(point => point.netCashAmount) }
    ]
  })
})

const usersOption = computed<EChartsOption>(() => {
  const trend = users.value?.trend || []
  return chartOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增用户', '下单用户', '支付用户'], textStyle: legendText, top: 6 },
    grid: { left: 12, right: 18, top: 48, bottom: 12, containLabel: true },
    xAxis: categoryAxis(trend.map(point => point.date.slice(5))),
    yAxis: valueAxis(),
    series: [
      { name: '新增用户', type: 'line', smooth: true, data: trend.map(point => point.newUsers) },
      { name: '下单用户', type: 'line', smooth: true, data: trend.map(point => point.orderingUsers) },
      { name: '支付用户', type: 'line', smooth: true, data: trend.map(point => point.payingUsers) }
    ]
  })
})

const ordersOption = computed<EChartsOption>(() => {
  const counts = orders.value?.counts
  const data = counts
    ? (Object.keys(statusLabels) as OrderStatus[]).map(status => ({ name: statusLabels[status], value: counts[status] }))
    : []
  return chartOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 笔（{d}%）' },
    legend: { type: 'scroll', bottom: 0, textStyle: legendText },
    series: [{ type: 'pie', radius: ['38%', '64%'], center: ['50%', '44%'], data, label: { color: '#193247' } }]
  })
})

const salesOption = computed<EChartsOption>(() => {
  const items = [...(sales.value?.items || [])].slice(0, 10)
  const names = items.map(item => item.name).reverse()
  return chartOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 24, top: 16, bottom: 12, containLabel: true },
    xAxis: valueAxis(),
    yAxis: { type: 'category', data: names, axisLabel: { color: '#193247', fontSize: 11 }, axisLine, axisTick: { show: false } },
    series: [{
      name: '销量（份）', type: 'bar', barMaxWidth: 18,
      data: items.map(item => item.quantity).reverse(),
      itemStyle: { color: '#0876ba', borderRadius: [0, 4, 4, 0] }
    }]
  })
})

const isEmpty = computed(() =>
  !!revenue.value && !!users.value && !!orders.value && !!sales.value &&
  !revenue.value.trend.length && !users.value.trend.length && !orders.value.trend.length && !sales.value.items.length
)

function range(days: number) {
  const day = new Date()
  day.setDate(day.getDate() - days + 1)
  begin.value = localDate(day)
  end.value = localDate()
  void load()
}

// 真实接口失败必须明确显示，绝不自动回退演示数据：捕获后清空所有结果并展示错误。
async function load() {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const [m, r, u, o, s] = await Promise.all([
      api.metrics(begin.value, end.value),
      api.revenueReport(begin.value, end.value),
      api.usersReport(begin.value, end.value),
      api.ordersReport(begin.value, end.value),
      api.salesReport({ begin: begin.value, end: end.value, topN: 10 })
    ])
    metrics.value = m; revenue.value = r; users.value = u; orders.value = o; sales.value = s
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '统计加载失败'
    metrics.value = revenue.value = users.value = orders.value = sales.value = null
  } finally {
    loading.value = false
  }
}

// 旧功能：浏览器本地 CSV 导出，保留原有列与口径。
function exportCsv() {
  const data = metrics.value
  if (!data) return
  downloadCsv(`园味经营统计_${data.period.begin}_${data.period.end}.csv`, [
    ['统计范围', data.period.begin, data.period.end], ['统计口径', data.metricNote], [],
    ['日期', '历史实付（元）', '支付订单（笔）'], ...data.trend.map(x => [x.date, x.revenue.toFixed(2), x.orders]), [],
    ['商品', '销量（份）', '销售额（元）'], ...data.sales.map(x => [x.name, x.quantity, x.amount.toFixed(2)])
  ])
}

async function exportExcel() {
  if (exporting.value || !begin.value || !end.value) return
  exporting.value = true
  notice.value = ''
  try {
    await api.exportReport('summary', begin.value, end.value)
    notice.value = API_MODE === 'mock' ? '演示模式已导出 CSV 文件（非 Excel）' : 'Excel 报表已开始下载'
  } catch (exception) {
    notice.value = exception instanceof Error ? exception.message : '导出失败'
  } finally {
    exporting.value = false
  }
}

onMounted(() => range(7))
</script>

<template>
  <form class="report-filter" @submit.prevent="load">
    <input v-model="begin" type="date" required aria-label="统计开始日期" />
    <span>至</span>
    <input v-model="end" type="date" required aria-label="统计结束日期" />
    <button class="btn primary" :disabled="loading" type="submit">查询统计</button>
    <div class="segmented">
      <button type="button" @click="range(1)">今天</button>
      <button type="button" @click="range(7)">近 7 天</button>
      <button type="button" @click="range(30)">近 30 天</button>
    </div>
    <button class="btn" type="button" style="margin-left:auto" :disabled="!metrics || loading" @click="exportCsv">
      <Icon name="download" :size="15" />导出 CSV
    </button>
    <button class="btn primary" type="button" :disabled="!begin || !end || loading || exporting" @click="exportExcel">
      <Icon name="download" :size="15" />{{ exporting ? '导出中…' : '导出 Excel' }}
    </button>
  </form>

  <div v-if="error" class="inline-error"><Icon name="error" :size="17" />{{ error }}</div>
  <div v-if="notice" class="inline-note section-gap"><Icon name="help" :size="17" />{{ notice }}</div>
  <div v-if="loading" class="loading-state"><Icon class="spin" name="loading" />正在统计…</div>

  <template v-else-if="isEmpty">
    <section class="panel section-gap">
      <EmptyState title="该时段暂无统计数据" description="试试调整日期范围，或确认后端已录入订单。" icon="reports" />
    </section>
  </template>

  <template v-else-if="revenue && users && orders && sales && metrics">
    <div class="metric-grid">
      <div class="metric-card highlight">
        <div class="metric-top"><span>历史实付总额</span><span class="metric-icon"><Icon name="reports" :size="17" /></span></div>
        <div class="metric-value"><small>¥</small>{{ money(metrics.revenue) }}</div>
        <div class="metric-note">按支付成功时间统计，含后来退款的订单</div>
      </div>
      <div class="metric-card">
        <div class="metric-top"><span>创建订单</span><span class="metric-icon"><Icon name="orders" :size="17" /></span></div>
        <div class="metric-value">{{ metrics.orderCount }}</div>
        <div class="metric-note">其中已支付 {{ metrics.paidOrders }} 笔（按支付时间）</div>
      </div>
      <div class="metric-card">
        <div class="metric-top"><span>平均实付</span><span class="metric-icon"><Icon name="payment" :size="17" /></span></div>
        <div class="metric-value"><small>¥</small>{{ money(metrics.averageAmount) }}</div>
        <div class="metric-note">收入 / 已支付订单数</div>
      </div>
      <div class="metric-card">
        <div class="metric-top"><span>取消订单占比</span><span class="metric-icon"><Icon name="orders" :size="17" /></span></div>
        <div class="metric-value">{{ metrics.cancellationRate.toFixed(1) }}<small>%</small></div>
        <div class="metric-note">按订单创建日期统计</div>
      </div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div><h2>营业额统计</h2><p>{{ revenue.metricNote }}</p></div>
        <span class="chart-legend">实付 ¥{{ money(revenue.grossPaidAmount) }} · 退款 ¥{{ money(revenue.refundAmount) }} · 净现金 ¥{{ money(revenue.netCashAmount) }}</span>
      </div>
      <EChart :option="revenueOption" height="320px" />
    </section>

    <div class="grid-two section-gap">
      <section class="panel">
        <div class="panel-head"><div><h2>用户统计</h2><p>{{ users.metricNote }}</p></div></div>
        <EChart :option="usersOption" height="300px" />
        <div class="summary-strip section-gap">
          <span>累计用户<strong>{{ users.totalUsers }}</strong></span>
          <span>区间新增<strong>{{ users.newUsers }}</strong></span>
          <span>下单用户<strong>{{ users.orderingUsers }}</strong></span>
          <span>支付用户<strong>{{ users.payingUsers }}</strong></span>
        </div>
      </section>
      <section class="panel">
        <div class="panel-head"><div><h2>订单统计 · 状态分布</h2><p>{{ orders.metricNote }}</p></div></div>
        <EChart :option="ordersOption" height="300px" />
        <div class="summary-strip section-gap">
          <span>完成率<strong>{{ orders.completionRate.toFixed(2) }}%</strong></span>
          <span>有效订单<strong>{{ orders.validOrders }}</strong></span>
          <span>已完成<strong>{{ orders.completedOrders }}</strong></span>
        </div>
      </section>
    </div>

    <section class="panel section-gap">
      <div class="panel-head">
        <div><h2>销量排名 Top10</h2><p>{{ sales.metricNote }}</p></div>
        <Icon name="dish" :size="18" style="color:var(--muted)" />
      </div>
      <EChart v-if="sales.items.length" :option="salesOption" height="340px" />
      <EmptyState v-else title="该时段暂无销量" description="试试其他日期范围。" icon="reports" />
      <div v-if="sales.items.length" class="table-scroll section-gap">
        <table>
          <thead><tr><th>排名</th><th>商品名称</th><th>销量</th><th>销售额</th></tr></thead>
          <tbody>
            <tr v-for="(item, index) in sales.items.slice(0, 10)" :key="item.id">
              <td>{{ String(index + 1).padStart(2, '0') }}</td>
              <td class="strong">{{ item.name }}</td>
              <td>{{ item.quantity }} 份</td>
              <td>¥{{ money(item.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div class="inline-note section-gap"><Icon name="help" :size="17" /><span>{{ revenue.metricNote }}</span></div>

    <div class="report-agent-button">
      <div><h3>让运营助手帮你读懂数据</h3><p>基于结构化指标，生成经营观察和建议。</p></div>
      <RouterLink to="/admin/agent?prompt=分析最近一周的营业额和菜品销量" class="btn primary"><Icon name="agent" :size="16" />打开 Agent</RouterLink>
    </div>
  </template>
</template>
