<script setup lang="ts">
import { computed } from 'vue'
import { money } from '@/domain/types'
const props = withDefaults(defineProps<{ values: { date: string; revenue: number; orders: number }[]; metric?: 'revenue' | 'orders' }>(), { metric: 'revenue' })
const max = computed(() => Math.max(10, ...props.values.map(x => x[props.metric])) * 1.15)
const points = computed(() => props.values.map((x, i) => `${55 + i * 660 / Math.max(props.values.length - 1, 1)},${185 - x[props.metric] / max.value * 150}`).join(' '))
const area = computed(() => props.values.length ? `M55,185 L${points.value.replaceAll(' ', ' L')} L${55 + (props.values.length - 1) * 660 / Math.max(props.values.length - 1, 1)},185 Z` : '')
</script>
<template>
  <div class="chart-wrap"><svg viewBox="0 0 760 235" class="trend-chart" role="img" :aria-label="metric === 'revenue' ? '收入趋势图' : '订单趋势图'">
    <defs><linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#65c1f5" stop-opacity=".3"/><stop offset="1" stop-color="#65c1f5" stop-opacity=".02"/></linearGradient></defs>
    <g v-for="i in 4" :key="i"><line x1="55" :y1="35 + (i - 1) * 50" x2="720" :y2="35 + (i - 1) * 50" stroke="var(--border)" stroke-dasharray="4 4"/><text x="43" :y="39 + (i - 1) * 50" text-anchor="end" fill="var(--muted)" font-size="10">{{ Math.round(max * (4 - i) / 3) }}</text></g>
    <path :d="area" fill="url(#chart-fill)"/><polyline :points="points" fill="none" stroke="var(--brand)" stroke-width="2.5" stroke-linejoin="round"/>
    <g v-for="(value, i) in values" :key="value.date"><circle :cx="55 + i * 660 / Math.max(values.length - 1, 1)" :cy="185 - value[metric] / max * 150" r="3.5" fill="#fff" stroke="var(--brand)" stroke-width="2"><title>{{ value.date }}：{{ metric === 'revenue' ? '¥' + money(value.revenue) : value.orders + ' 笔' }}</title></circle><text v-if="values.length <= 14 || i % Math.ceil(values.length / 10) === 0" :x="55 + i * 660 / Math.max(values.length - 1, 1)" y="216" text-anchor="middle" fill="var(--muted)" font-size="10">{{ value.date.slice(5).replace('-', '/') }}</text></g>
  </svg></div>
</template>
