<script setup lang="ts">
import { ref, watch } from 'vue'
import FoodArt from './FoodArt.vue'

const props = defineProps<{ image?: string; name: string; art: string }>()
const failed = ref(false)
watch(() => props.image, () => { failed.value = false })
</script>

<template>
  <div class="menu-product-image">
    <img v-if="image && !failed" :src="image" :alt="name" loading="lazy" @error="failed = true" />
    <FoodArt v-else :kind="art" crop />
  </div>
</template>
