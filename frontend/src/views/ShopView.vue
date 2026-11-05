<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { app, cartAmount, cartCount, changeCart, refreshBase, refreshShop, safely } from '@/stores/app'
import { money, type MenuItem } from '@/domain/types'
import { availableQuantity } from '@/domain/rules'
import FoodArt from '@/components/FoodArt.vue'
import Icon from '@/components/Icon.vue'
import EmptyState from '@/components/EmptyState.vue'
import Modal from '@/components/Modal.vue'

const route = useRoute(); const kind = ref('all'); const category = ref(0); const taste = ref(''); const budget = ref(''); const search = ref(String(route.query.q || '')); const detail = ref<MenuItem | null>(null); const busy = ref(false); const loading = ref(true); const error = ref('')
const categories = computed(() => app.categories.filter(x => kind.value === 'all' || x.type === (kind.value === 'dish' ? 1 : 2)))
const items = computed(() => app.menu.filter(x => (kind.value === 'all' || x.kind === kind.value) && (!category.value || x.categoryId === category.value) && (!taste.value || x.tags.includes(taste.value)) && (!budget.value || x.price <= Number(budget.value)) && (!search.value || x.name.includes(search.value) || x.tags.some(t => t.includes(search.value)))))
async function load() { loading.value = true; error.value = ''; try { await refreshBase() } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false } }
async function add(item: MenuItem) { if (busy.value) return; busy.value = true; await safely(() => changeCart(item.id, 1), `${item.name}已加入购物车`); busy.value = false }
watch(kind, () => { category.value = 0 })
watch(() => route.query.q, q => { search.value = String(q || '') })
onMounted(load)
</script>
<template>
  <div class="menu-hero"><div class="hero-copy"><span class="badge">FRESH EVERYDAY</span><h2>一餐好味，给忙碌充个电。</h2><p>选一份喜欢的午餐，让今天的工作日更有滋味。</p><RouterLink to="/user/agent" class="text-link" style="margin-top:16px">不知道吃什么？问问点餐助手<Icon name="arrow" :size="14"/></RouterLink></div><div class="hero-illustration"><FoodArt kind="salad"/></div></div>
  <div v-if="app.shopError" class="inline-error" role="alert">
    {{ app.shopError }}
    <button class="btn text" @click="refreshShop">重试营业状态</button>
  </div>
  <div v-if="app.shop && !app.shop.open" class="inline-note" style="margin-bottom:20px"><Icon name="clock" :size="17"/>食堂当前休息中，可以浏览菜单，暂时不能提交订单。营业时段：{{ app.shop.openingHours }}</div>
  <div class="toolbar"><div class="segmented"><button :class="{ active: kind === 'all' }" @click="kind = 'all'">全部菜单</button><button :class="{ active: kind === 'setmeal' }" @click="kind = 'setmeal'">套餐</button><button :class="{ active: kind === 'dish' }" @click="kind = 'dish'">单品菜品</button></div><div class="filters"><div class="search-field"><Icon name="search" :size="15"/><input v-model="search" placeholder="搜索想吃的菜品" aria-label="搜索想吃的菜品"/></div><select v-model="taste" aria-label="口味筛选"><option value="">全部口味</option><option>不辣</option><option>清淡</option><option>素食</option><option>高蛋白</option></select><select v-model="budget" aria-label="预算筛选"><option value="">不限预算</option><option value="20">20 元以内</option><option value="30">30 元以内</option><option value="40">40 元以内</option></select></div></div>
  <div class="filter-tabs"><button :class="{ active: category === 0 }" @click="category = 0">全部分类</button><button v-for="c in categories" :key="c.id" :class="{ active: category === c.id }" @click="category = c.id">{{ c.name }}</button></div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="16"/>{{ error }}<button class="btn text" @click="load">重试</button></div><div v-if="loading" class="loading-state"><Icon name="loading" class="spin"/>加载今日菜单…</div>
  <div v-else-if="items.length" class="food-grid"><article v-for="item in items" :key="item.id" class="food-card"><div class="food-image" @click="detail = item"><FoodArt :kind="item.art"/><span class="badge">{{ item.kind === 'setmeal' ? '精选套餐' : '现制单品' }}</span></div><div class="food-card-body"><div class="food-card-title"><button class="btn text" style="padding:0;color:inherit" @click="detail = item"><h3>{{ item.name }}</h3></button><span class="stock-note">{{ availableQuantity(item, app.menu) }} 份可售</span></div><p class="food-card-description">{{ item.description }}</p><div class="tag-row"><span class="chip" v-for="tag in item.tags" :key="tag">{{ tag }}</span></div><div class="food-card-footer"><span class="price"><small>¥</small>{{ money(item.price) }}</span><button class="add-btn" :aria-label="'加入购物车：' + item.name" :disabled="busy || availableQuantity(item, app.menu) <= 0" @click="add(item)"><Icon name="plus" :size="17"/></button></div></div></article></div><section v-else class="panel"><EmptyState title="没有找到合适的菜单" description="试试调整预算、分类或口味条件。"/></section>
  <RouterLink v-if="cartCount" to="/user/cart" class="sticky-cart"><Icon name="cart" :size="23"/><div><small>{{ cartCount }} 件商品</small><strong>¥{{ money(cartAmount) }}</strong></div><span style="margin-left:10px;font-size:12px">去结算</span><Icon name="arrow" :size="17"/></RouterLink>
  <Modal v-if="detail" :title="detail.name" @close="detail = null"><FoodArt :kind="detail.art" style="border-radius:10px;margin-bottom:18px"/><p style="font-size:12px;color:#8d9b7c;margin-bottom:14px">{{ detail.description }}</p><div class="tag-row"><span class="chip" v-for="tag in detail.tags" :key="tag">{{ tag }}</span></div><div v-if="detail.kind === 'setmeal'" class="section-gap"><h3>套餐组成</h3><div v-for="part in detail.composition" :key="part.dishId" class="summary-row"><span>{{ app.menu.find(x => x.id === part.dishId)?.name || '组成菜品暂不可售' }}</span><span>× {{ part.quantity }}</span></div></div><p class="metric-detail">当前可售 {{ availableQuantity(detail, app.menu) }} 份。商品插画为示意图；下单时会重新检查价格与库存。</p><template #footer><span class="price" style="margin-right:auto"><small>¥</small>{{ money(detail.price) }}</span><button class="btn primary" :disabled="busy || availableQuantity(detail, app.menu) <= 0" @click="add(detail)"><Icon name="plus" :size="16"/>加入购物车</button></template></Modal>
</template>
