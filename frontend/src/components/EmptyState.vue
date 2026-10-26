<script setup lang="ts">
import Icon from './Icon.vue'
withDefaults(defineProps<{ title?: string; description?: string; icon?: string }>(), { title: '暂无数据', description: '试试调整筛选条件，或添加一条新记录。', icon: 'dish' })
</script>
<template><div class="empty-state"><div class="empty-icon"><Icon :name="icon" :size="30" /></div><h3>{{ title }}</h3><p>{{ description }}</p><slot /></div></template>
