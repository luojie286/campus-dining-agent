<script setup lang="ts">
import Modal from './Modal.vue'
import Icon from './Icon.vue'
withDefaults(defineProps<{ title: string; message: string; busy?: boolean; confirmLabel?: string; danger?: boolean }>(), { confirmLabel: '确认操作' })
defineEmits<{ close: []; confirm: [] }>()
</script>
<template><Modal :title="title" :busy="busy" @close="$emit('close')"><p style="font-size:13px;color:#889777;line-height:1.9;white-space:pre-wrap">{{ message }}</p><template #footer><button class="btn" :disabled="busy" @click="$emit('close')">暂不操作</button><button class="btn" :class="danger ? 'danger' : 'primary'" :disabled="busy" @click="$emit('confirm')"><Icon v-if="busy" name="loading" class="spin" :size="16"/>{{ confirmLabel }}</button></template></Modal></template>
