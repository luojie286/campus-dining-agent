<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api'
import { loadDishChoices } from '@/api/dishChoices'
import { app, refreshCategories, toast } from '@/stores/app'
import { money, type MenuItem } from '@/domain/types'
import { menuFormError } from '@/domain/menuForm'
import { availableQuantity } from '@/domain/rules'
import Icon from '@/components/Icon.vue'
import FoodArt from '@/components/FoodArt.vue'
import Modal from '@/components/Modal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'

const props = defineProps<{ kind: 'dish' | 'setmeal' }>()
const route = useRoute()
const name = ref(String(route.query.q || ''))
const categoryId = ref<number | undefined>()
const status = ref('')
const page = ref(1)
const pageSize = ref(8)
const total = ref(0)
const records = ref<MenuItem[]>([])
const loading = ref(false)
const error = ref('')
const categoryError = ref('')
const detailLoading = ref(false)
const formError = ref('')
const form = ref<MenuItem | null>(null)
const busy = ref(false)
const deleting = ref<MenuItem | null>(null)
const failedImages = ref(new Set<string>())
let requestVersion = 0
let detailVersion = 0
const label = computed(() => props.kind === 'dish' ? '菜品' : '套餐')
const categories = computed(() => app.categories.filter(x => x.type === (props.kind === 'dish' ? 1 : 2)))
const dishes = ref<MenuItem[]>([])
const dishError = ref('')
const dishesLoading = ref(false)
let dishVersion = 0
async function loadDishes(): Promise<void> {
  const version = ++dishVersion
  dishesLoading.value = true
  dishError.value = ''
  try {
    const result = await loadDishChoices(page => api.menu('dish', { page, pageSize: 100 }))
    if (version === dishVersion) dishes.value = result
  } catch (exception) {
    if (version !== dishVersion) return
    dishes.value = []
    dishError.value = exception instanceof Error ? exception.message : '菜品加载失败'
  } finally {
    if (version === dishVersion) dishesLoading.value = false
  }
}
function quantity(item: MenuItem): number | string {
  if (item.kind === 'dish') return item.stock
  if (dishesLoading.value || dishError.value || categoryError.value) return '—'
  const categoryEnabled = (id: number) => app.categories.some(category => category.id === id && category.status === 1)
  if (!categoryEnabled(item.categoryId)) return 0
  const availableDishes = dishes.value.filter(dish => categoryEnabled(dish.categoryId))
  return availableQuantity(item, availableDishes)
}

async function load(reset = false): Promise<void> {
  if (reset) page.value = 1
  const version = ++requestVersion
  if (props.kind === 'setmeal') await loadDishes()
  if (version !== requestVersion) return
  loading.value = true
  error.value = ''
  try {
    let result = await api.menu(props.kind, { name: name.value.trim(), categoryId: categoryId.value, status: status.value, page: page.value, pageSize: pageSize.value })
    if (version !== requestVersion) return
    // 删除尾页记录后回到最新最后一页，不用过时请求覆盖新筛选结果。
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      result = await api.menu(props.kind, { name: name.value.trim(), categoryId: categoryId.value, status: status.value, page: page.value, pageSize: pageSize.value })
      if (version !== requestVersion) return
    }
    records.value = result.records
    total.value = result.total
  } catch (exception) {
    if (version !== requestVersion) return
    error.value = exception instanceof Error ? exception.message : '加载失败'
    records.value = []
    total.value = 0
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
async function loadCategories(): Promise<void> {
  categoryError.value = ''
  try { await refreshCategories() }
  catch (exception) { categoryError.value = exception instanceof Error ? exception.message : '分类加载失败' }
}
async function edit(item?: MenuItem): Promise<void> {
  if (busy.value || detailLoading.value) return
  formError.value = ''
  if (!item) {
    form.value = { id: 0, kind: props.kind, name: '', categoryId: categories.value.find(category => category.status === 1)?.id || 0, price: 0, description: '', status: 0, stock: 0, tags: [], art: 'chicken', composition: [] }
    return
  }
  const version = ++detailVersion
  detailLoading.value = true
  try {
    const current = await api.menuItem(item.kind, item.id)
    if (version === detailVersion) form.value = current
  } catch (exception) {
    toast(exception instanceof Error ? exception.message : '读取详情失败', 'error')
  } finally {
    if (version === detailVersion) detailLoading.value = false
  }
}
async function save(): Promise<void> {
  if (!form.value || busy.value) return
  formError.value = menuFormError(form.value) || ''
  if (formError.value) return
  busy.value = true
  try {
    await api.saveMenu(form.value)
    // 写入成功与列表刷新分开处理，刷新失败不能误报为保存失败。
    form.value = null
    toast(`${label.value}已保存`)
    await load()
  } catch (exception) {
    formError.value = exception instanceof Error ? exception.message : '保存失败'
  } finally { busy.value = false }
}
async function toggle(item: MenuItem): Promise<void> {
  if (busy.value || detailLoading.value) return
  busy.value = true
  try {
    const targetStatus = item.status ? 0 : 1
    await api.setMenuStatus(item, targetStatus)
    toast(targetStatus ? '已起售' : '已停售')
    await load()
  } catch (exception) {
    toast(exception instanceof Error ? exception.message : '状态更新失败', 'error')
  } finally { busy.value = false }
}
async function remove(): Promise<void> {
  if (!deleting.value || busy.value) return
  busy.value = true
  try {
    await api.deleteMenu(deleting.value)
    deleting.value = null
    toast('商品已删除')
    await load()
  } catch (exception) {
    toast(exception instanceof Error ? exception.message : '删除失败', 'error')
  } finally { busy.value = false }
}
watch([categoryId, status, pageSize], () => { void load(true) })
watch(() => route.query.q, q => { name.value = String(q || ''); void load(true) })
watch(() => props.kind, () => {
  detailVersion++
  detailLoading.value = false
  form.value = null
  deleting.value = null
  dishVersion++
  dishes.value = []
  void loadCategories()
  void load(true)
})
onMounted(async () => {
  await loadCategories()
  await load()
})
</script>
<template>
  <div class="toolbar"><form class="filters" @submit.prevent="load(true)"><div class="search-field"><Icon name="search" :size="16"/><input v-model="name" :aria-label="'搜索' + label" :placeholder="'搜索' + label + '名称'"/></div><select v-model="categoryId" aria-label="筛选分类"><option :value="undefined">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select><select v-model="status" aria-label="筛选售卖状态"><option value="">全部状态</option><option value="1">起售中</option><option value="0">已停售</option></select><button class="btn" type="submit"><Icon name="search" :size="15"/>查询</button></form><button class="btn primary" :disabled="busy || detailLoading || !categories.length || (kind === 'setmeal' && (dishesLoading || !!dishError))" @click="edit()"><Icon name="plus" :size="17"/>新增{{ label }}</button></div>
  <div v-if="kind === 'setmeal' && dishError" class="inline-error" role="alert">{{ dishError }}<button class="btn text" @click="loadDishes">重试菜品</button></div><div v-if="kind === 'setmeal' && dishesLoading" class="loading-state">正在读取组成菜品…</div><div v-if="categoryError" class="inline-error">{{ categoryError }}<button class="btn text" @click="loadCategories">重试分类</button></div><div v-if="detailLoading" class="loading-state">正在读取最新详情…</div><div class="summary-strip"><span>当前结果<strong>{{ total }} 个{{ label }}</strong></span><span>库存按服务端当前数据展示</span><span v-if="kind === 'setmeal'">可售量按当前目录估算，购买时由服务端校验</span></div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/>{{ error }}<button class="btn text" @click="load()">重试</button></div>
  <section class="panel table-panel"><div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>加载菜单…</div><div v-else class="table-scroll"><table v-if="records.length"><thead><tr><th>{{ label }}信息</th><th>分类</th><th>价格</th><th>库存（份）</th><th>口味标签</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in records" :key="item.id"><td><div class="table-product"><img v-if="item.image && !failedImages.has(item.image)" :src="item.image" :alt="item.name" class="food-art" style="object-fit:cover" @error="failedImages.add(item.image!)"/><FoodArt v-else :kind="item.art"/><div><strong>{{ item.name }}</strong><span class="table-sub">{{ kind === 'setmeal' ? item.composition.length + ' 个组成菜品' : '商品编号 ' + item.id }}</span></div></div></td><td>{{ app.categories.find(x => x.id === item.categoryId)?.name || '未分类' }}</td><td class="strong">¥{{ money(item.price) }}</td><td>{{ quantity(item) }}<span v-if="item.kind === 'setmeal'" class="table-sub">独立库存 {{ item.stock }}</span></td><td><div class="tag-row"><span v-for="tag in item.tags" :key="tag" class="chip">{{ tag }}</span><span v-if="!item.tags.length" class="muted">未维护</span></div></td><td><span class="badge" :class="item.status ? 'green' : 'gray'">{{ item.status ? '起售中' : '已停售' }}</span></td><td><div class="row-actions"><button class="btn text" :disabled="busy || detailLoading" @click="edit(item)">编辑</button><button class="btn text" :disabled="busy || detailLoading" @click="toggle(item)">{{ item.status ? '停售' : '起售' }}</button><button class="icon-btn danger" :disabled="busy || detailLoading" :aria-label="'删除' + item.name" @click="deleting = item"><Icon name="delete" :size="16"/></button></div></td></tr></tbody></table><EmptyState v-else :title="'没有找到' + label" description="调整筛选条件，或新增菜单商品。"/></div><select v-model.number="pageSize" aria-label="每页条数"><option :value="8">8条/页</option><option :value="20">20条/页</option><option :value="100">100条/页</option></select><Pagination :page="page" :total="total" :page-size="pageSize" :disabled="loading || busy" @change="p => { page = p; load() }"/></section>
  <Modal v-if="form" :title="(form.id ? '编辑' : '新增') + label" wide :busy="busy" @close="!busy && (form = null)"><div v-if="formError" class="inline-error" role="alert">{{ formError }}</div><fieldset :disabled="busy" style="border:0;padding:0;margin:0"><div class="field-grid"><label class="field"><span>{{ label }}名称</span><input v-model="form.name" maxlength="50" placeholder="输入商品名称" required/></label><label class="field"><span>所属分类</span><select v-model="form.categoryId"><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label><label class="field"><span>售价（元）</span><input v-model.number="form.price" type="number" min="0.01" max="9999" step="0.01"/></label><label class="field"><span>{{ form.id ? '当前库存（只读）' : '初始库存（份）' }}</span><input :disabled="!!form.id" v-model.number="form.stock" type="number" min="0" step="1"/></label><label class="field"><span>售卖状态</span><select v-model="form.status"><option :value="0">已停售</option><option :value="1">起售中</option></select></label><label class="field"><span>示意插画</span><select v-model="form.art"><option value="chicken">鸡腿</option><option value="fish">鲜鱼</option><option value="beef">牛肉</option><option value="salad">蔬菜</option><option value="noodle">面食</option><option value="tofu">豆腐</option></select><small>当前使用本地 SVG 插画，不代表真实商品照片。</small></label></div><label class="field"><span>商品描述</span><textarea v-model="form.description" maxlength="300" placeholder="描述组成与口味，未知信息不要填写保证。"/></label><div class="field"><span>口味标签</span></div><div class="checkbox-row"><label v-for="tag in ['不辣', '微辣', '辣', '清淡', '素食', '高蛋白']" :key="tag"><input v-model="form.tags" type="checkbox" :value="tag"/>{{ tag }}</label></div><template v-if="kind === 'setmeal'"><div class="form-divider"></div><div class="panel-head"><h3>套餐组成</h3><button class="btn small" :disabled="!dishes.length || dishesLoading" @click="form.composition.push({ dishId: dishes[0]?.id || 0, quantity: 1 })"><Icon name="plus" :size="14"/>添加菜品</button></div><div v-for="(part, i) in form.composition" :key="i" class="composition-row"><select v-model="part.dishId" :aria-label="'组成菜品' + (i + 1)"><option :value="0" disabled>请选择菜品</option><option v-for="dish in dishes" :key="dish.id" :value="dish.id">{{ dish.name }}{{ dish.status ? '' : '（已停售）' }}</option></select><input v-model.number="part.quantity" type="number" min="1" max="99" :aria-label="'组成数量' + (i + 1)"/><button class="icon-btn danger" aria-label="移除组成菜品" @click="form.composition.splice(i, 1)"><Icon name="delete" :size="16"/></button></div><p class="metric-detail">可售份数受套餐独立库存和组成菜品库存共同限制。新增套餐默认停售。</p></template></fieldset><template #footer><button class="btn" :disabled="busy" @click="form = null">取消</button><button class="btn primary" :disabled="busy" @click="save"><Icon v-if="busy" class="spin" name="loading" :size="15"/>保存{{ label }}</button></template></Modal>
  <ConfirmDialog v-if="deleting" title="删除商品" :message="`确定删除「${deleting.name}」？\n起售商品、被套餐引用的菜品及关联未完成订单的商品不能删除。`" danger :busy="busy" @close="!busy && (deleting = null)" @confirm="remove"/>
</template>
