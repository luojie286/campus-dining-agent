<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, LegendScrollComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, LegendScrollComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  option: EChartsOption
  height?: string
}>(), { height: '300px' })

const container = ref<HTMLDivElement | null>(null)
let chart: ReturnType<typeof init> | null = null

// 与浅色 UI 协调：品牌蓝主色系、深色文字；坐标轴/网格由各图显式设置为深色可读文字与浅色分割线。
const palette = ['#0876ba', '#3f9bd0', '#6db98f', '#e0a763', '#b6709e', '#57a6a6', '#8d9fb1', '#c2d3e3']
const textColor = '#193247'

function render(): void {
  if (!chart) return
  const themed: EChartsOption = {
    ...props.option,
    color: props.option.color ?? palette,
    backgroundColor: 'transparent',
    textStyle: { color: textColor }
  }
  // notMerge=true 让每次 option 更新完全替换，避免切换数据时残留旧系列。
  chart.setOption(themed, true)
}

function resize(): void { chart?.resize() }

onMounted(() => {
  if (!container.value) return
  chart = init(container.value)
  render()
  window.addEventListener('resize', resize)
})

watch(() => props.option, () => render())

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="container" class="echart" :style="{ height }" role="img" aria-label="统计图表"></div>
</template>

<style scoped>
.echart { width: 100%; }
</style>
