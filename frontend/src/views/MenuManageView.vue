<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api'
import { app, refreshBase, safely, toast } from '@/stores/app'
import { money, type MenuItem } from '@/domain/types'
import { availableQuantity } from '@/domain/rules'
import Icon from '@/components/Icon.vue'
import FoodArt from '@/components/FoodArt.vue'
import Modal from '@/components/Modal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { cloneRecord } from '@/utils/clone'

const props = defineProps<{ kind: 'dish' | 'setmeal' }>()
const route = useRoute(); const name = ref(String(route.query.q || '')); const categoryId = ref<number | undefined>(); const status = ref(''); const page = ref(1); const total = ref(0); const records = ref<MenuItem[]>([]); const loading = ref(false); const error = ref(''); const form = ref<MenuItem | null>(null); const busy = ref(false); const deleting = ref<MenuItem | null>(null)
const label = computed(() => props.kind === 'dish' ? '菜品' : '套餐')
const categories = computed(() => app.categories.filter(x => x.type === (props.kind === 'dish' ? 1 : 2)))
const dishes = computed(() => app.menu.filter(x => x.kind === 'dish'))
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true; error.value = ''
  try { const response = await api.menu(props.kind, { name: name.value.trim(), categoryId: categoryId.value, status: status.value, page: page.value, pageSize: 8 }); records.value = response.records; total.value = response.total }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败' }
  finally { loading.value = false }
}
function edit(item?: MenuItem) {
  form.value = item ? cloneRecord(item) : { id: 0, kind: props.kind, name: '', categoryId: categories.value[0]?.id || 0, price: 0, description: '', status: 0, stock: 0, tags: [], art: 'chicken', composition: [] }
}
async function save() {
  if (!form.value || busy.value) return
  if (form.value.tags.includes('不辣') && (form.value.tags.includes('辣') || form.value.tags.includes('微辣'))) { toast('辣度标签相互冲突，请检查', 'error'); return }
  busy.value = true
  await safely(async () => { await api.saveMenu(form.value!); form.value = null; await refreshBase(); await load() }, `${label.value}已保存`)
  busy.value = false
}
async function toggle(item: MenuItem) { await safely(async () => { await api.saveMenu({ ...item, status: item.status ? 0 : 1 }); await refreshBase(); await load() }, item.status ? '已停售' : '已起售') }
async function remove() { if (!deleting.value) return; busy.value = true; await safely(async () => { await api.deleteMenu(deleting.value!); deleting.value = null; await refreshBase(); await load(true) }, '商品已删除'); busy.value = false }
watch([categoryId, status], () => { void load(true) })
watch(() => route.query.q, q => { name.value = String(q || ''); void load(true) })
onMounted(async () => { if (!app.menu.length) await safely(refreshBase); await load() })
</script>
<template>
  <div class="toolbar"><form class="filters" @submit.prevent="load(true)"><div class="search-field"><Icon name="search" :size="16"/><input v-model="name" :aria-label="'搜索' + label" :placeholder="'搜索' + label + '名称'"/></div><select v-model="categoryId" aria-label="筛选分类"><option :value="undefined">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select><select v-model="status" aria-label="筛选售卖状态"><option value="">全部状态</option><option value="1">起售中</option><option value="0">已停售</option></select><button class="btn" type="submit"><Icon name="search" :size="15"/>查询</button></form><button class="btn primary" @click="edit()"><Icon name="plus" :size="17"/>新增{{ label }}</button></div>
  <div class="summary-strip"><span>当前结果<strong>{{ total }} 个{{ label }}</strong></span><span>菜单更新后，员工端同步展示</span><span v-if="kind === 'setmeal'">套餐由真实菜品组成</span></div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/>{{ error }}<button class="btn text" @click="load()">重试</button></div>
  <section class="panel table-panel"><div v-if="loading" class="loading-state"><Icon class="spin" name="loading"/>加载菜单…</div><div v-else class="table-scroll"><table v-if="records.length"><thead><tr><th>{{ label }}信息</th><th>分类</th><th>价格</th><th>可售份数</th><th>口味标签</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in records" :key="item.id"><td><div class="table-product"><FoodArt :kind="item.art"/><div><strong>{{ item.name }}</strong><span class="table-sub">{{ kind === 'setmeal' ? item.composition.length + ' 个组成菜品' : '商品编号 ' + item.id }}</span></div></div></td><td>{{ app.categories.find(x => x.id === item.categoryId)?.name || '未分类' }}</td><td class="strong">¥{{ money(item.price) }}</td><td>{{ availableQuantity(item, app.menu) }}<span class="table-sub">独立库存 {{ item.stock }}</span></td><td><div class="tag-row"><span v-for="tag in item.tags" :key="tag" class="chip">{{ tag }}</span><span v-if="!item.tags.length" class="muted">未维护</span></div></td><td><span class="badge" :class="item.status ? 'green' : 'gray'">{{ item.status ? '起售中' : '已停售' }}</span></td><td><div class="row-actions"><button class="btn text" @click="edit(item)">编辑</button><button class="btn text" @click="toggle(item)">{{ item.status ? '停售' : '起售' }}</button><button class="icon-btn danger" :aria-label="'删除' + item.name" @click="deleting = item"><Icon name="delete" :size="16"/></button></div></td></tr></tbody></table><EmptyState v-else :title="'没有找到' + label" description="调整筛选条件，或新增菜单商品。"/></div><Pagination :page="page" :total="total" @change="p => { page = p; load() }"/></section>
  <Modal v-if="form" :title="(form.id ? '编辑' : '新增') + label" wide :busy="busy" @close="!busy && (form = null)"><div class="field-grid"><label class="field"><span>{{ label }}名称</span><input v-model="form.name" maxlength="50" placeholder="输入商品名称" required/></label><label class="field"><span>所属分类</span><select v-model="form.categoryId"><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label><label class="field"><span>售价（元）</span><input v-model.number="form.price" type="number" min="0.01" max="9999" step="0.01"/></label><label class="field"><span>独立可售库存（份）</span><input v-model.number="form.stock" type="number" min="0" step="1"/></label><label class="field"><span>售卖状态</span><select v-model="form.status"><option :value="0">已停售</option><option :value="1">起售中</option></select></label><label class="field"><span>示意插画</span><select v-model="form.art"><option value="chicken">鸡腿</option><option value="fish">鲜鱼</option><option value="beef">牛肉</option><option value="salad">蔬菜</option><option value="noodle">面食</option><option value="tofu">豆腐</option></select><small>当前使用本地 SVG 插画，不代表真实商品照片。</small></label></div><label class="field"><span>商品描述</span><textarea v-model="form.description" maxlength="300" placeholder="描述组成与口味，未知信息不要填写保证。"/></label><div class="field"><span>口味标签</span></div><div class="checkbox-row"><label v-for="tag in ['不辣', '微辣', '辣', '清淡', '素食', '高蛋白']" :key="tag"><input v-model="form.tags" type="checkbox" :value="tag"/>{{ tag }}</label></div><template v-if="kind === 'setmeal'"><div class="form-divider"></div><div class="panel-head"><h3>套餐组成</h3><button class="btn small" @click="form.composition.push({ dishId: dishes[0]?.id || 0, quantity: 1 })"><Icon name="plus" :size="14"/>添加菜品</button></div><div v-for="(part, i) in form.composition" :key="i" class="composition-row"><select v-model="part.dishId" :aria-label="'组成菜品' + (i + 1)"><option v-for="dish in dishes" :key="dish.id" :value="dish.id">{{ dish.name }}{{ dish.status ? '' : '（已停售）' }}</option></select><input v-model.number="part.quantity" type="number" min="1" max="99" :aria-label="'组成数量' + (i + 1)"/><button class="icon-btn danger" aria-label="移除组成菜品" @click="form.composition.splice(i, 1)"><Icon name="delete" :size="16"/></button></div><p class="metric-detail">可售份数受套餐独立库存和组成菜品库存共同限制。新增套餐默认停售。</p></template><template #footer><button class="btn" :disabled="busy" @click="form = null">取消</button><button class="btn primary" :disabled="busy" @click="save"><Icon v-if="busy" class="spin" name="loading" :size="15"/>保存{{ label }}</button></template></Modal>
  <ConfirmDialog v-if="deleting" title="删除商品" :message="`确定删除「${deleting.name}」？\n演示规则：起售商品、被套餐引用的菜品及关联未完成订单的商品不能删除。`" danger :busy="busy" @close="!busy && (deleting = null)" @confirm="remove"/>
</template>
