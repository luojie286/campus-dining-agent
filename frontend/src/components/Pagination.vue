<script setup lang="ts">
import Icon from './Icon.vue'
withDefaults(defineProps<{ page: number; total: number; pageSize?: number; disabled?: boolean }>(), { pageSize: 8, disabled: false })
defineEmits<{ change: [page: number] }>()
</script>
<template><div class="pagination"><span>共 {{ total }} 条记录</span><div><button class="icon-btn" aria-label="上一页" :disabled="disabled || page <= 1" @click="$emit('change', page - 1)"><Icon name="left" :size="16"/></button><span class="page-current">{{ page }}</span><span class="muted">/ {{ Math.max(1, Math.ceil(total / pageSize)) }}</span><button class="icon-btn" aria-label="下一页" :disabled="disabled || page * pageSize >= total" @click="$emit('change', page + 1)"><Icon name="right" :size="16"/></button></div></div></template>
