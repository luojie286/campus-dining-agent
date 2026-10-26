<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '@/api'
import { refreshCategories, safely, toast } from '@/stores/app'
import { initialPasswordError } from '@/domain/operatorPassword'
import type { Category, Operator } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import Modal from '@/components/Modal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import { cloneRecord } from '@/utils/clone'

const props = defineProps<{ entity: 'category' | 'employee' }>()
// 密码只保留在新增弹窗中，不放进账号列表、编辑资料或浏览器存储。
const initialPassword = ref(''); const confirmPassword = ref(''); const operatorError = ref('')
const categories = ref<Category[]>([]); const operators = ref<Operator[]>([]); const search = ref(''); const categoryForm = ref<Category | null>(null); const operatorForm = ref<Operator | null>(null); const deleting = ref<Category | null>(null); const busy = ref(false); const error = ref(''); const loading = ref(true)
const isCategory = computed(() => props.entity === 'category')
const page = ref(1); const pageSize = ref(8); const total = ref(0); const status = ref<'' | 0 | 1>('')
const togglingId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const detailError = ref('')
const categoryError = ref('')
const categoryType = ref<'' | 1 | 2>('')
let editRequestId = 0
let loadId = 0; let searchTimer: ReturnType<typeof setTimeout> | undefined
const rows = computed(() => isCategory.value
  ? categories.value.map(c => ({ id: c.id, name: c.name, sub: '分类编号 ' + c.id, type: c.type === 1 ? '菜品分类' : '套餐分类', value: String(c.sort), status: c.status }))
  : operators.value.map(o => ({ id: o.id, name: o.name, sub: o.username, type: o.accountRole === 'ACCOUNT_ADMIN' ? '账号管理员' : '普通运营', value: o.phone, status: o.status })))
async function load(reset = false): Promise<void> {
  clearTimeout(searchTimer)
  if (reset) page.value = 1
  const current = ++loadId
  error.value = ''; loading.value = true
  try {
    if (isCategory.value) {
      const result = await api.categoriesPage({
        page: page.value,
        pageSize: pageSize.value,
        name: search.value.trim(),
        type: categoryType.value === '' ? undefined : categoryType.value,
        status: status.value === '' ? undefined : status.value
      })
      if (current !== loadId) return
      categories.value = result.records
      total.value = result.total
    } else {
      const result = await api.operatorsPage({ page: page.value, pageSize: pageSize.value, name: search.value.trim(), status: status.value === '' ? undefined : status.value })
      if (current !== loadId) return
      // 筛选或账号状态变更导致尾页消失时，回到最后一个有效页。
      const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
      if (page.value > lastPage) { page.value = lastPage; await load(); return }
      operators.value = result.records; total.value = result.total
    }
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
    }
  } catch (e) {
    if (current !== loadId) return
    error.value = e instanceof Error ? e.message : '加载失败'
    if (isCategory.value) categories.value = []
    else operators.value = []
    total.value = 0
  } finally { if (current === loadId) loading.value = false }
}
function changePage(nextPage: number) {
  if (loading.value || togglingId.value !== null || nextPage < 1 || nextPage > Math.max(1, Math.ceil(total.value / pageSize.value))) return
  page.value = nextPage; void load()
}
function reloadFilters(delay: number) {
  clearTimeout(searchTimer)
  // 防抖等待期间也使旧请求失效，避免旧结果闪回。
  ++loadId; page.value = 1; loading.value = true; error.value = ''
  if (delay) searchTimer = setTimeout(() => { void load() }, delay)
  else void load()
}
watch(search, () => reloadFilters(300))
watch([status, pageSize, categoryType], () => reloadFilters(0))
async function syncCategoryCache() {
  try {
    await refreshCategories()
  } catch (error) {
    const message = error instanceof Error ? error.message : '请求失败'
    toast(`分类操作已成功，但下拉分类同步失败：${message}`, 'error')
  }
}
async function edit(id?: number) {
  if (busy.value || togglingId.value !== null || editingId.value !== null) return
  initialPassword.value = ''
  confirmPassword.value = ''
  operatorError.value = ''
  detailError.value = ''
  categoryError.value = ''

  if (isCategory.value) {
    categoryForm.value = id
      ? cloneRecord(categories.value.find(category => category.id === id)!)
      : { id: 0, name: '', type: 1, sort: 0, status: 1 }
    return
  }

  if (!id) {
    operatorForm.value = { id: 0, username: '', name: '', phone: '', status: 1 }
    return
  }

  const currentRequest = ++editRequestId
  editingId.value = id
  try {
    // 点击编辑时读取最新资料，不用分页记录充当详情。
    const operator = await api.operator(id)
    if (currentRequest !== editRequestId) return
    operatorForm.value = cloneRecord(operator)
  } catch (error) {
    if (currentRequest !== editRequestId) return
    const message = error instanceof Error ? error.message : '读取账号资料失败'
    detailError.value = `${message}。请重新点击编辑以重试。`
  } finally {
    if (currentRequest === editRequestId) editingId.value = null
  }
}
function closeOperator() {
  if (busy.value) return
  operatorForm.value = null; initialPassword.value = ''; confirmPassword.value = ''; operatorError.value = ''
}
async function save() {
  if (busy.value || (!operatorForm.value && !categoryForm.value)) return

  if (categoryForm.value) {
    categoryError.value = ''
    const category = categoryForm.value
    const name = category.name.trim()
    if (!name || name.length > 30) {
      categoryError.value = '分类名称必须为1到30个字符'
      return
    }
    if (!Number.isInteger(category.sort) || category.sort < 0 || category.sort > 2147483647) {
      categoryError.value = '排序值必须为0到2147483647的整数'
      return
    }
  }

  if (operatorForm.value) {
    operatorError.value = ''
    const operator = operatorForm.value
    const name = operator.name.trim()
    if (!name || name.length > 30) {
      operatorError.value = '姓名必须为1到30个字符'
      return
    }
    if (!/^1\d{10}$/.test(operator.phone)) {
      operatorError.value = '手机号必须为11位大陆手机号'
      return
    }
    if (!operator.id) {
      const username = operator.username.trim()
      if (!username || username.length > 30) {
        operatorError.value = '登录账号必须为1到30个字符'
        return
      }
      operatorError.value = initialPasswordError(initialPassword.value, confirmPassword.value)
      if (operatorError.value) return
    }
  }

  busy.value = true
  const createdOperator = !!operatorForm.value && !operatorForm.value.id
  const createdCategory = !!categoryForm.value && categoryForm.value.id === 0
  const savingCategory = !!categoryForm.value
  try {
    if (categoryForm.value) {
      await api.saveCategory({ ...categoryForm.value, name: categoryForm.value.name.trim() })
    } else if (operatorForm.value) {
      const password = createdOperator ? initialPassword.value : undefined
      await api.saveOperator(operatorForm.value, password)
    }

    categoryForm.value = null
    operatorForm.value = null
    initialPassword.value = ''
    confirmPassword.value = ''
    operatorError.value = ''
    await load(createdOperator || createdCategory)
    if (savingCategory) await syncCategoryCache()
    toast('记录已保存')
  } catch (error) {
    const message = error instanceof Error ? error.message : '保存失败'
    // 失败时保留弹窗和用户输入，便于修改或再次提交。
    if (operatorForm.value) operatorError.value = message
    else if (categoryForm.value) categoryError.value = message
    else toast(message, 'error')
  } finally {
    busy.value = false
  }
}
async function toggle(id: number) {
  if (togglingId.value !== null || editingId.value !== null || busy.value || loading.value) return
  togglingId.value = id
  try {
    await safely(async () => {
      if (isCategory.value) {
        const c = categories.value.find(x => x.id === id)
        if (!c) throw new Error('分类不存在，请刷新列表')
        const saved = await api.setCategoryStatus(id, c.status === 1 ? 0 : 1)
        categories.value = categories.value.map(item => item.id === id ? saved : item)
      } else {
        const operator = operators.value.find(x => x.id === id)
        if (!operator) throw new Error('运营账号不存在，请刷新列表')
        const saved = await api.setOperatorStatus(id, operator.status === 1 ? 0 : 1)
        operators.value = operators.value.map(x => x.id === id ? saved : x)
      }
      await load()
      if (isCategory.value) await syncCategoryCache()
    }, '状态已更新')
  } finally { togglingId.value = null }
}
async function remove() {
  if (!deleting.value || busy.value || togglingId.value !== null) return
  busy.value = true
  try {
    await safely(async () => {
      await api.deleteCategory(deleting.value!.id)
      deleting.value = null
      await load()
      await syncCategoryCache()
    }, '分类已删除')
  } finally {
    busy.value = false
  }
}
onMounted(() => { void load() })
onBeforeUnmount(() => { clearTimeout(searchTimer); ++loadId; ++editRequestId })
</script>
<template>
  <div class="toolbar">
    <div class="filters">
      <div class="search-field">
        <Icon name="search" :size="16"/>
        <input v-model="search" aria-label="搜索记录" :maxlength="100" :placeholder="isCategory ? '搜索分类名称' : '搜索姓名或账号'"/>
      </div>
      <select v-if="isCategory" v-model="categoryType" aria-label="分类类型">
        <option value="">全部类型</option>
        <option :value="1">菜品分类</option>
        <option :value="2">套餐分类</option>
      </select>
      <select v-model="status" :aria-label="isCategory ? '分类状态' : '账号状态'">
        <option value="">全部状态</option>
        <option :value="1">已启用</option>
        <option :value="0">已停用</option>
      </select>
      <select v-model.number="pageSize" aria-label="每页条数">
        <option :value="8">每页 8 条</option>
        <option :value="10">每页 10 条</option>
        <option :value="20">每页 20 条</option>
        <option :value="50">每页 50 条</option>
        <option :value="100">每页 100 条</option>
      </select>
    </div>
    <button class="btn primary" :disabled="togglingId !== null || editingId !== null || busy" @click="edit()">
      <Icon name="plus" :size="17"/>{{ isCategory ? '新增分类' : '新增运营人员' }}
    </button>
  </div>
  <div v-if="error" class="inline-error"><Icon name="error" :size="17"/>{{ error }}<button class="btn text" @click="load()">重试</button></div>
  <div v-if="detailError" class="inline-error" role="alert"><Icon name="error" :size="17"/>{{ detailError }}</div>
  <section class="panel table-panel"><div v-if="loading" class="loading-state"><Icon name="loading" class="spin"/>加载记录…</div><div v-else class="table-scroll"><table v-if="rows.length"><thead><tr><th>{{ isCategory ? '分类名称' : '姓名 / 账号' }}</th><th>类型</th><th>{{ isCategory ? '排序' : '手机号' }}</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td class="strong">{{ row.name }}<span class="table-sub">{{ row.sub }}</span></td><td>{{ row.type }}</td><td>{{ row.value }}</td><td><span class="badge" :class="row.status ? 'green' : 'gray'">{{ row.status ? '已启用' : '已停用' }}</span></td><td><div class="row-actions"><button class="btn text" :disabled="togglingId !== null || editingId !== null || busy" @click="edit(row.id)">{{ editingId === row.id ? '读取中…' : '编辑' }}</button><button class="btn text" :disabled="togglingId !== null || editingId !== null || busy" :aria-busy="togglingId === row.id" @click="toggle(row.id)">{{ togglingId === row.id ? '更新中…' : row.status ? '停用' : '启用' }}</button><button v-if="isCategory" class="icon-btn danger" :aria-label="'删除分类' + row.name" @click="deleting = categories.find(x => x.id === row.id) || null"><Icon name="delete" :size="16"/></button></div></td></tr></tbody></table><EmptyState v-else title="没有匹配记录" description="调整搜索条件，或新增记录。" :icon="isCategory ? 'categories' : 'users'"/></div><Pagination :page="page" :total="total" :page-size="pageSize" :disabled="loading || !!error || togglingId !== null" @change="changePage"/></section>
  <div v-if="!isCategory" class="inline-note section-gap"><Icon name="shield" :size="16"/>此页面由账号管理员维护普通运营账号，新增时设置初始密码。启用或停用账号请使用列表按钮，编辑资料不修改密码。</div>
  <Modal v-if="categoryForm" :title="categoryForm.id ? '编辑分类' : '新增分类'" :busy="busy" @close="!busy && (categoryForm = null)"><label class="field"><span>分类名称</span><input v-model="categoryForm.name" maxlength="30"/></label><div class="field-grid"><label class="field"><span>分类类型</span><select v-model="categoryForm.type"><option :value="1">菜品分类</option><option :value="2">套餐分类</option></select></label><label class="field"><span>排序</span><input v-model.number="categoryForm.sort" type="number" min="0" max="2147483647" step="1"/></label></div><label class="field checkbox"><input v-model="categoryForm.status" type="checkbox" :true-value="1" :false-value="0"/><span>启用分类</span></label><div v-if="categoryError" class="inline-error" role="alert">{{ categoryError }}</div><template #footer><button class="btn" :disabled="busy" @click="categoryForm = null">取消</button><button class="btn primary" :disabled="busy" @click="save">保存分类</button></template></Modal>
  <Modal v-if="operatorForm" :title="operatorForm.id ? '编辑运营人员' : '新增运营人员'" :busy="busy" @close="closeOperator">
    <label class="field"><span>登录账号</span><input v-model="operatorForm.username" maxlength="30" :disabled="busy || !!operatorForm.id" autocomplete="off"/></label>
    <template v-if="!operatorForm.id">
      <label class="field"><span>初始密码</span><input v-model="initialPassword" type="password" minlength="8" maxlength="64" autocomplete="new-password" :disabled="busy" aria-describedby="operator-password-hint"/><small id="operator-password-hint">8～64 个字符，UTF-8 不超过 72 字节；密码中的空格会保留。</small></label>
      <label class="field"><span>确认密码</span><input v-model="confirmPassword" type="password" maxlength="64" autocomplete="new-password" :disabled="busy" placeholder="请再次输入初始密码"/></label>
    </template>
    <label class="field"><span>姓名</span><input v-model="operatorForm.name" maxlength="30" :disabled="busy"/></label>
    <label class="field"><span>手机号</span><input v-model="operatorForm.phone" type="tel" maxlength="11" :disabled="busy"/></label>
    <label v-if="!operatorForm.id" class="field checkbox"><input v-model="operatorForm.status" type="checkbox" :true-value="1" :false-value="0" :disabled="busy"/><span>启用账号</span></label>
    <div v-if="operatorError" class="inline-error" role="alert">{{ operatorError }}</div>
    <template #footer><button class="btn" :disabled="busy" @click="closeOperator">取消</button><button class="btn primary" :disabled="busy" @click="save">{{ busy ? '保存中…' : '保存人员' }}</button></template>
  </Modal>
  <ConfirmDialog v-if="deleting" title="删除分类" :message="`确定删除「${deleting.name}」？有关联商品时，操作会被拒绝。`" danger :busy="busy" @close="!busy && (deleting = null)" @confirm="remove"/>
</template>
