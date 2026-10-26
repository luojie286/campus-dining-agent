<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
</script>
<template><section class="panel"><EmptyState title="这个页面不在菜单上" description="请通过左侧导航返回工作空间。" icon="search"><RouterLink to="/" class="btn primary">返回首页</RouterLink></EmptyState></section></template>
