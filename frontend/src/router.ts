import { createRouter, createWebHistory } from 'vue-router'
import { readSession } from '@/api/http'
import { canManageOperators } from '@/domain/permissions'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/', redirect: () => readSession()?.role === 'admin' ? '/admin/dashboard' : '/user/menu' },
    { path: '/admin/dashboard', component: () => import('@/views/DashboardView.vue'), meta: { role: 'admin', title: '工作台', subtitle: '掌握园区餐饮的每一个日常' } },
    { path: '/admin/dishes', component: () => import('@/views/MenuManageView.vue'), props: { kind: 'dish' }, meta: { role: 'admin', title: '菜品管理', subtitle: '管理菜单，让每一餐都有好滋味' } },
    { path: '/admin/setmeals', component: () => import('@/views/MenuManageView.vue'), props: { kind: 'setmeal' }, meta: { role: 'admin', title: '套餐管理', subtitle: '把好味道组合成一份完整的午餐' } },
    { path: '/admin/categories', component: () => import('@/views/ManageView.vue'), props: { entity: 'category' }, meta: { role: 'admin', title: '分类管理', subtitle: '让菜单井然有序' } },
    { path: '/admin/employees', component: () => import('@/views/ManageView.vue'), props: { entity: 'employee' }, meta: { role: 'admin', requiresAccountManagement: true, title: '运营人员', subtitle: '维护普通运营账号与启用状态' } },
    { path: '/admin/orders', component: () => import('@/views/OrdersView.vue'), meta: { role: 'admin', title: '订单管理', subtitle: '从下单到送达，每一步都有记录' } },
    { path: '/admin/reports', component: () => import('@/views/ReportsView.vue'), meta: { role: 'admin', title: '经营统计', subtitle: '用经营指标理解经营变化' } },
    { path: '/admin/agent', component: () => import('@/views/AgentView.vue'), meta: { role: 'admin', title: '运营 Agent', subtitle: '连接业务数据的餐饮运营助手' } },
    { path: '/user/menu', component: () => import('@/views/ShopView.vue'), meta: { role: 'user', title: '今日菜单', subtitle: '忙碌的日子，也要认真吃饭' } },
    { path: '/user/cart', component: () => import('@/views/CartView.vue'), meta: { role: 'user', title: '购物车', subtitle: '确认这一餐的好味道' } },
    { path: '/user/orders', component: () => import('@/views/OrdersView.vue'), meta: { role: 'user', title: '我的订单', subtitle: '查看你的订单与取餐进度' } },
    { path: '/user/addresses', component: () => import('@/views/AddressesView.vue'), meta: { role: 'user', title: '收餐地址', subtitle: '把午餐送到你身边' } },
    { path: '/user/agent', component: () => import('@/views/AgentView.vue'), meta: { role: 'user', title: '点餐 Agent', subtitle: '说说想吃什么，帮你找一顿合适的午餐' } },
    { path: '/settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '接口与环境', subtitle: '查看运行模式、连接情况与学习入口' } },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面未找到' } }
  ]
})
router.beforeEach(to => {
  if (to.meta.public) return true
  const session = readSession()
  if (!session) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.role && to.meta.role !== session.role) return session.role === 'admin' ? '/admin/dashboard' : '/user/menu'
  if (to.meta.requiresAccountManagement && !canManageOperators(session)) return '/admin/dashboard'
  return true
})
router.afterEach(to => { document.title = `${String(to.meta.title || '园区餐饮')} · 园味` })
