<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import Icon from './Icon.vue'
defineProps<{ title: string; wide?: boolean; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLElement | null>(null)
let previous: HTMLElement | null = null
let previousOverflow = ''
function handleKey(event: KeyboardEvent) {
  if (document.querySelectorAll('[role="dialog"]').item(document.querySelectorAll('[role="dialog"]').length - 1) !== dialog.value) return
  if (event.key === 'Escape') emit('close')
  if (event.key === 'Tab' && dialog.value) {
    const focusables = [...dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]')]
    const first = focusables[0], last = focusables.at(-1)
    if (event.shiftKey && document.activeElement === first) { last?.focus(); event.preventDefault() }
    else if (!event.shiftKey && document.activeElement === last) { first?.focus(); event.preventDefault() }
  }
}
onMounted(() => { previous = document.activeElement as HTMLElement; previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; dialog.value?.querySelector<HTMLElement>('input,button')?.focus(); document.addEventListener('keydown', handleKey) })
onBeforeUnmount(() => { document.body.style.overflow = previousOverflow; document.removeEventListener('keydown', handleKey); previous?.focus() })
</script>
<template>
  <Teleport to="body"><div class="modal-backdrop" @mousedown.self="!busy && emit('close')"><section ref="dialog" class="modal" :class="{ wide }" role="dialog" aria-modal="true" :aria-label="title"><header class="modal-head"><h2>{{ title }}</h2><button class="icon-btn" aria-label="关闭弹窗" :disabled="busy" @click="emit('close')"><Icon name="close" /></button></header><div class="modal-body"><slot /></div><footer v-if="$slots.footer" class="modal-footer"><slot name="footer" /></footer></section></div></Teleport>
</template>
