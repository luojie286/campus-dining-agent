<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { API_MODE } from '@/api/http'
import { app, cartCount, refreshBase, safely, setSession } from '@/stores/app'
import { localDate } from '@/domain/types'
import { notifications } from '@/api/notifications'
import { canManageOperators, sessionRoleLabel } from '@/domain/permissions'

const route = useRoute(); const router = useRouter(); const collapsed = ref(false); const mobileOpen = ref(false); const search = ref('')
const admin = computed(() => app.session?.role === 'admin')
const adminGroups = [
  { label: '门店运营', links: [
    { path: '/admin/dashboard', icon: 'dashboard', label: '工作台' },
    { path: '/admin/orders', icon: 'orders', label: '订单管理' },
  ] },
  { label: '商品管理', links: [
    { path: '/admin/dishes', icon: 'dish', label: '菜品管理' },
    { path: '/admin/setmeals', icon: 'setmeal', label: '套餐管理' },
    { path: '/admin/categories', icon: 'categories', label: '分类管理' },
  ] },
  { label: '门店管理', links: [
    { path: '/admin/reports', icon: 'reports', label: '经营统计' },
    { path: '/admin/employees', icon: 'users', label: '运营人员' }
  ] }
]
const userLinks = [
  { path: '/user/menu', icon: 'dish', label: '今日菜单' },
  { path: '/user/cart', icon: 'cart', label: '购物车' },
  { path: '/user/orders', icon: 'orders', label: '我的订单' },
  { path: '/user/addresses', icon: 'address', label: '收餐地址' }
]
const visibleAdminGroups = computed(() => adminGroups.map(group => ({
  ...group,
  links: group.links.filter(link => link.path !== '/admin/employees' || canManageOperators(app.session))
})))
const accountMenu = ref<HTMLDetailsElement | null>(null)
const roleLabel = computed(() => sessionRoleLabel(app.session))
const isLogin = computed(() => route.path === '/login')
function logout() { setSession(null); router.push('/login') }
function doSearch() {
  if (!search.value.trim()) return
  router.push({ path: '/admin/dishes', query: { q: search.value.trim() } }); search.value = ''
}
watch(() => app.session, session => {
  notifications.connect(session)
  if (!session && !isLogin.value) router.push('/login')
  else if (session && route.meta.requiresAccountManagement && !canManageOperators(session)) router.replace('/admin/dashboard')
})
watch(() => route.path, () => {
  mobileOpen.value = false
  if (accountMenu.value) accountMenu.value.open = false
})
onMounted(async () => {
  // 首次路由解析完成后再决定需要哪些基础数据，避免直达菜品页时误加载合并菜单。
  await router.isReady()
  notifications.connect(app.session)
  if (app.session && !['/admin/dishes', '/admin/setmeals'].includes(route.path)) await safely(refreshBase)
})
</script>

<template>
  <RouterView v-if="isLogin" />
  <div v-else-if="!admin" class="customer-shell">
    <header class="customer-header">
      <div class="customer-header-inner">
        <RouterLink to="/user/menu" class="brand customer-brand">
          <span class="brand-mark"><Icon name="dish" :size="21" /></span>
          <div class="brand-copy"><strong>园味<span> CAMPUS</span></strong><small>园区点餐</small></div>
        </RouterLink>
        <nav class="customer-nav" aria-label="点餐导航">
          <RouterLink v-for="link in userLinks" :key="link.path" :to="link.path">
            <span class="customer-nav-icon">
              <Icon :name="link.icon" :size="19" />
              <span v-if="link.icon === 'cart' && cartCount" class="customer-nav-count">{{ cartCount }}</span>
            </span>
            <span>{{ link.label }}</span>
          </RouterLink>
        </nav>
        <div class="customer-header-actions">
          <RouterLink to="/user/agent" class="customer-assistant" title="点餐助手">
            <Icon name="agent" :size="17" /><span>点餐助手</span>
          </RouterLink>
          <details ref="accountMenu" class="customer-account">
            <summary aria-label="账号与设置">
              <span class="avatar small">{{ app.session?.name.slice(0, 1) }}</span>
              <Icon name="down" :size="12" />
            </summary>
            <div class="customer-account-menu">
              <strong>{{ app.session?.name }}</strong>
              <RouterLink to="/settings"><Icon name="settings" :size="16" />接口与环境</RouterLink>
              <button type="button" @click="logout"><Icon name="logout" :size="16" />退出登录</button>
            </div>
          </details>
        </div>
      </div>
    </header>
    <main class="customer-content">
      <div v-if="route.path !== '/user/menu'" class="customer-page-title">
        <h1>{{ route.meta.title }}</h1><p>{{ route.meta.subtitle }}</p>
      </div>
      <RouterView v-slot="{ Component }">
        <component :is="Component" :key="route.path + app.sessionRevision" />
      </RouterView>
    </main>
    <footer class="customer-footer">
      <span>园味 · 园区食堂</span>
      <RouterLink to="/settings">{{ API_MODE === 'mock' ? '演示数据' : '真实接口' }}</RouterLink>
    </footer>
  </div>
  <div v-else class="app-shell" :class="{ collapsed }">
    <div v-if="mobileOpen" class="sidebar-overlay" @click="mobileOpen = false"></div>
    <aside class="sidebar" :class="{ 'mobile-open': mobileOpen }">
      <RouterLink to="/admin/dashboard" class="brand"><span class="brand-mark"><Icon name="sprout" :size="25" /></span><div class="brand-copy"><strong>园味<span> CAMPUS</span></strong><small>智能园区餐饮平台</small></div></RouterLink>
      <div class="workspace"><span class="workspace-icon"><Icon name="store" :size="18" /></span><div class="nav-copy"><strong>{{ app.shop?.name || '园区食堂' }}</strong><small>餐饮运营工作空间</small></div><Icon class="nav-copy" name="down" :size="15" /></div>
      <nav class="main-nav" aria-label="运营导航">
        <div v-for="group in visibleAdminGroups" :key="group.label" class="admin-nav-group">
          <div class="nav-section-label">{{ group.label }}</div>
          <RouterLink v-for="link in group.links" :key="link.path" :to="link.path" :title="link.label">
            <Icon :name="link.icon" /><span class="nav-copy">{{ link.label }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="nav-section-label agent-label">智能助手</div>
      <RouterLink to="/admin/agent" class="agent-nav" :class="{ active: route.path.endsWith('/agent') }"><Icon name="agent" /><span class="nav-copy">运营 Agent</span><span class="tiny-label nav-copy">AI</span></RouterLink>
      <div class="sidebar-bottom"><div class="sidebar-note nav-copy"><Icon name="shield" :size="18"/><p>园区食堂<br><span>运营工作空间</span></p></div><RouterLink to="/settings" class="settings-nav"><Icon name="settings"/><span class="nav-copy">接口与环境</span></RouterLink><div class="profile"><span class="avatar">{{ app.session?.name.slice(0, 1) }}</span><div class="nav-copy"><strong>{{ app.session?.name }}</strong><small>{{ roleLabel }}</small></div><button class="icon-btn nav-copy" title="退出登录 / 切换身份" aria-label="退出登录" @click="logout"><Icon name="logout" :size="18"/></button></div></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><div class="topbar-left"><button class="icon-btn desktop-toggle" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" @click="collapsed = !collapsed"><Icon :name="collapsed ? 'expand' : 'collapse'" :size="19"/></button><button class="icon-btn mobile-toggle" aria-label="打开导航" @click="mobileOpen = true"><Icon name="expand"/></button><span class="breadcrumb">运营中心</span><Icon name="right" :size="14"/><strong>{{ route.meta.title }}</strong></div><div class="topbar-right"><form class="global-search" @submit.prevent="doSearch"><Icon name="search" :size="16"/><input v-model="search" aria-label="搜索菜单" placeholder="搜索菜品…"/><kbd>↵</kbd></form><RouterLink to="/settings" class="mode-badge"><span class="dot"></span>{{ API_MODE === 'mock' ? '演示数据' : '真实接口' }}</RouterLink><button class="icon-btn notification-btn" aria-label="查看订单通知" @click="router.push('/admin/orders')"><Icon name="bell"/><span v-if="notifications.unread" class="notification-dot"></span></button><span class="avatar small">{{ app.session?.name.slice(0, 1) }}</span></div></header>
      <main class="page-content"><div class="page-heading"><div><p class="eyebrow">园味商家中心</p><h1>{{ route.meta.title }}</h1><p class="subtitle">{{ route.meta.subtitle }}</p></div><div class="heading-date"><Icon name="clock" :size="15"/><span>{{ localDate() }}<br><small>{{ new Date().toLocaleDateString('zh-CN', { weekday: 'long' }) }}</small></span></div></div><RouterView v-slot="{ Component }"><component :is="Component" :key="route.path + app.sessionRevision" /></RouterView></main>
      <footer class="page-footer"><span>园味 · 每一餐，都值得认真对待</span><span>{{ API_MODE === 'mock' ? '本地演示 · 支付与 Agent 均为模拟' : '业务数据由后端提供' }}<span v-if="API_MODE === 'api'"> · 通知{{ notifications.connected ? '已连接' : '未连接' }}</span></span></footer>
    </div>
  </div>
  <Teleport to="body"><div class="toast-stack" role="status" aria-live="polite"><div v-for="item in app.toasts" :key="item.id" class="toast" :class="item.type"><Icon :name="item.type === 'error' ? 'error' : 'success'" :size="19"/><span>{{ item.message }}</span><button class="icon-btn" aria-label="关闭提示" @click="app.toasts = app.toasts.filter(x => x.id !== item.id)"><Icon name="close" :size="15"/></button></div></div></Teleport>
</template>
