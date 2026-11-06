<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { app, cartAmount, cartCount, changeCart, refreshBase, refreshShop, safely } from '@/stores/app'
import { money, type MenuItem } from '@/domain/types'
import { availableQuantity } from '@/domain/rules'
import FoodArt from '@/components/FoodArt.vue'
import MenuProductImage from '@/components/MenuProductImage.vue'
import Icon from '@/components/Icon.vue'
import EmptyState from '@/components/EmptyState.vue'
import Modal from '@/components/Modal.vue'

const route = useRoute()
const kind = ref<'all' | MenuItem['kind']>('all')
const category = ref(0)
const taste = ref('')
const budget = ref('')
const search = ref(String(route.query.q || ''))
const detail = ref<MenuItem | null>(null)
const busy = ref(false)
const loading = ref(true)
const error = ref('')

const menuTypes = [
  { value: 'all', label: '全部' },
  { value: 'setmeal', label: '套餐' },
  { value: 'dish', label: '单品' }
] as const

const categories = computed(() => app.categories.filter(item =>
  kind.value === 'all' || item.type === (kind.value === 'dish' ? 1 : 2)
))

const items = computed(() => {
  const keyword = search.value.trim()
  return app.menu.filter(item =>
    (kind.value === 'all' || item.kind === kind.value) &&
    (!category.value || item.categoryId === category.value) &&
    (!taste.value || item.tags.includes(taste.value)) &&
    (!budget.value || item.price <= Number(budget.value)) &&
    (!keyword || item.name.includes(keyword) || item.tags.some(tag => tag.includes(keyword)))
  )
})

const menuGroups = computed(() => {
  const itemsByCategory = new Map<number, MenuItem[]>()
  for (const item of items.value) {
    const group = itemsByCategory.get(item.categoryId)
    if (group) group.push(item)
    else itemsByCategory.set(item.categoryId, [item])
  }

  const groups: { id: number; name: string; items: MenuItem[] }[] = []
  for (const category of categories.value) {
    const categoryItems = itemsByCategory.get(category.id)
    if (!categoryItems) continue
    groups.push({ id: category.id, name: category.name, items: categoryItems })
    itemsByCategory.delete(category.id)
  }

  const uncategorized = [...itemsByCategory.values()].flat()
  if (uncategorized.length) groups.push({ id: 0, name: '其他菜品', items: uncategorized })
  return groups
})

const productStock = computed(() =>
  new Map(items.value.map(item => [item.id, availableQuantity(item, app.menu)]))
)
function stockOf(item: MenuItem): number {
  return productStock.value.get(item.id) ?? 0
}

const cartQuantities = computed(() => new Map(app.cart.map(line => [line.itemId, line.quantity])))
const shopStatus = computed(() => {
  if (!app.shop) return '营业状态未知'
  return app.shop.open ? '营业中' : '休息中'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    await refreshBase()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function add(item: MenuItem) {
  if (busy.value) return
  busy.value = true
  try {
    await safely(() => changeCart(item.id, 1), `${item.name}已加入购物车`)
  } finally {
    busy.value = false
  }
}

watch(kind, () => { category.value = 0 })
watch(() => route.query.q, query => { search.value = String(query || '') })
onMounted(load)
</script>

<template>
  <div class="store-page">
    <section class="store-banner" aria-labelledby="store-name">
      <div class="store-banner-copy">
        <div class="store-banner-label"><Icon name="store" :size="16" /> 园区点餐</div>
        <div class="store-banner-title">
          <h1 id="store-name">{{ app.shop?.name || '园区食堂' }}</h1>
          <span class="store-status" :class="{ 'is-open': app.shop?.open }">
            <span class="store-status-dot"></span>{{ shopStatus }}
          </span>
        </div>
        <p class="store-service"><Icon name="truck" :size="16" /> 园区配送 <span>·</span> 到店自提</p>
        <p v-if="app.shop?.openingHours" class="store-hours"><Icon name="clock" :size="15" /> {{ app.shop.openingHours }}</p>
      </div>
      <div class="store-banner-art" aria-hidden="true">
        <div class="store-art-ring"></div>
        <FoodArt kind="chicken" crop />
      </div>
    </section>

    <div v-if="app.shopError" class="inline-error" role="alert">
      {{ app.shopError }}
      <button class="btn text" @click="refreshShop">重试营业状态</button>
    </div>
    <div v-if="app.shop && !app.shop.open" class="inline-note store-notice">
      <Icon name="clock" :size="17" />食堂当前休息中，可以浏览菜单，暂时不能提交订单。
    </div>

    <section class="store-menu" aria-labelledby="store-menu-title">
      <div class="store-menu-toolbar">
        <div class="store-menu-heading">
          <h2 id="store-menu-title">店内点餐</h2>
          <div class="store-kind-tabs" role="group" aria-label="商品类型">
            <button v-for="tab in menuTypes" :key="tab.value" type="button"
              :class="{ active: kind === tab.value }" :aria-pressed="kind === tab.value" @click="kind = tab.value">
              {{ tab.label }}
            </button>
          </div>
        </div>
        <div class="store-filters">
          <label class="search-field store-search">
            <Icon name="search" :size="16" />
            <input v-model="search" type="search" placeholder="搜索本店菜品" aria-label="搜索本店菜品" />
          </label>
          <select v-model="taste" aria-label="口味筛选">
            <option value="">全部口味</option>
            <option>不辣</option><option>清淡</option><option>素食</option><option>高蛋白</option>
          </select>
          <select v-model="budget" aria-label="预算筛选">
            <option value="">不限预算</option>
            <option value="20">20 元以内</option><option value="30">30 元以内</option><option value="40">40 元以内</option>
          </select>
        </div>
      </div>

      <div class="store-menu-layout">
        <nav class="store-category-rail" aria-label="菜品分类">
          <span class="store-category-label">菜品分类</span>
          <button type="button" :class="{ active: category === 0 }" :aria-pressed="category === 0" @click="category = 0">
            <Icon name="categories" :size="16" />全部分类
          </button>
          <button v-for="item in categories" :key="item.id" type="button"
            :class="{ active: category === item.id }" :aria-pressed="category === item.id" @click="category = item.id">
            {{ item.name }}
          </button>
        </nav>

        <div class="store-menu-results">
          <div v-if="error" class="inline-error" role="alert">
            <Icon name="error" :size="16" />{{ error }}<button class="btn text" @click="load">重试</button>
          </div>
          <div v-if="loading" class="loading-state"><Icon name="loading" class="spin" />加载今日菜单…</div>
          <template v-else-if="menuGroups.length">
            <section v-for="group in menuGroups" :key="group.id" class="store-product-group">
              <div class="store-group-heading"><h3>{{ group.name }}</h3><span>{{ group.items.length }} 项</span></div>
              <div class="store-product-list">
                <article v-for="item in group.items" :key="item.id" class="store-product">
                  <button type="button" class="store-product-media" :aria-label="'查看详情：' + item.name" @click="detail = item">
                    <MenuProductImage :image="item.image" :name="item.name" :art="item.art" />
                  </button>
                  <div class="store-product-body">
                    <button type="button" class="store-product-name" @click="detail = item">{{ item.name }}</button>
                    <p class="store-product-description">{{ item.description }}</p>
                    <div class="tag-row store-product-tags">
                      <span class="chip" v-for="tag in item.tags" :key="tag">{{ tag }}</span>
                    </div>
                    <div class="store-product-footer">
                      <div class="store-product-price">
                        <span class="price"><small>¥</small>{{ money(item.price) }}</span>
                        <span class="store-product-stock">{{ stockOf(item) ? stockOf(item) + ' 份可售' : '暂不可售' }}</span>
                      </div>
                      <div class="store-product-action">
                        <span v-if="cartQuantities.get(item.id)" class="store-product-quantity">{{ cartQuantities.get(item.id) }}</span>
                        <button type="button" class="add-btn" :aria-label="'加入购物车：' + item.name"
                          :disabled="busy || stockOf(item) <= 0" @click="add(item)">
                          <Icon name="plus" :size="18" />
                        </button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </template>
          <EmptyState v-else-if="!error" title="没有找到合适的菜单" description="试试调整预算、分类或口味条件。" />
        </div>
      </div>
    </section>

    <div class="store-cart-position">
      <div class="store-cart-bar">
        <RouterLink to="/user/cart" class="store-cart-summary" aria-label="查看购物车">
          <span class="store-cart-icon"><Icon name="cart" :size="22" /></span>
          <div>
            <strong v-if="cartCount">¥{{ money(cartAmount) }}</strong>
            <strong v-else>购物车为空</strong>
            <small v-if="cartCount">已选 {{ cartCount }} 件商品</small>
            <small v-else>添加喜欢的菜品</small>
          </div>
        </RouterLink>
        <RouterLink v-if="cartCount" to="/user/cart" class="store-cart-checkout">
          去结算 <Icon name="right" :size="15" />
        </RouterLink>
        <span v-else class="store-cart-empty">去结算</span>
      </div>
    </div>

    <Modal v-if="detail" :title="detail.name" @close="detail = null">
      <MenuProductImage class="store-detail-image" :image="detail.image" :name="detail.name" :art="detail.art" />
      <p class="store-detail-description">{{ detail.description }}</p>
      <div class="tag-row"><span class="chip" v-for="tag in detail.tags" :key="tag">{{ tag }}</span></div>
      <div v-if="detail.kind === 'setmeal'" class="section-gap">
        <h3>套餐组成</h3>
        <div v-for="part in detail.composition" :key="part.dishId" class="summary-row">
          <span>{{ app.menu.find(item => item.id === part.dishId)?.name || '组成菜品暂不可售' }}</span><span>× {{ part.quantity }}</span>
        </div>
      </div>
      <p class="metric-detail">当前可售 {{ availableQuantity(detail, app.menu) }} 份。下单时会重新检查价格与库存。</p>
      <template #footer>
        <span class="price store-detail-price"><small>¥</small>{{ money(detail.price) }}</span>
        <button class="btn primary" :disabled="busy || availableQuantity(detail, app.menu) <= 0" @click="add(detail)">
          <Icon name="plus" :size="16" />加入购物车
        </button>
      </template>
    </Modal>
  </div>
</template>
