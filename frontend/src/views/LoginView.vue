<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { app, refreshBase, setSession, toast } from '@/stores/app'
import Icon from '@/components/Icon.vue'
import type { Role } from '@/domain/types'

const role = ref<Role>('admin'); const username = ref(API_MODE === 'mock' ? 'admin' : ''); const password = ref(API_MODE === 'mock' ? 'admin123' : '')
const busy = ref(false); const error = ref(''); const router = useRouter(); const route = useRoute()
watch(role, value => { username.value = API_MODE === 'mock' ? (value === 'admin' ? 'admin' : 'staff') : ''; password.value = API_MODE === 'mock' ? (value === 'admin' ? 'admin123' : '123456') : ''; error.value = '' })
async function login() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const session = await api.login(role.value, username.value.trim(), password.value)
    if (session.role !== role.value) throw new Error('登录返回的角色与入口不一致，请检查后端登录协议')
    setSession(session)
    const target = String(route.query.redirect || '')
    const destination = target.startsWith(`/${session.role}/`) ? target : session.role === 'admin' ? '/admin/dashboard' : '/user/menu'
    // 菜品管理独立加载分类及分页，不因其他未完成的基础接口误报登录失败。
    if (destination !== '/admin/dishes') {
      try { await refreshBase() } catch (e) { toast(e instanceof Error ? e.message : '基础接口尚未实现', 'info') }
    }
    await router.push(destination)
  } catch (e) { error.value = e instanceof Error ? e.message : '登录失败'; if (app.session) setSession(null) }
  finally { busy.value = false }
}
</script>
<template>
  <div class="login-page"><section class="login-story"><div class="brand inverse"><span class="brand-mark"><Icon name="sprout" :size="27"/></span><div class="brand-copy"><strong>园味<span> CAMPUS</span></strong><small>智能园区餐饮平台</small></div></div><div class="story-copy"><span class="story-label"><span class="dot"></span>让园区的每一餐，更有滋味</span><h1>好好吃饭。<br><em>轻松运营。</em></h1><p>从一份用心的午餐，到一次有依据的经营决策。<br>连接员工、食堂与智能助手，让日常更从容。</p><div class="story-features"><span><Icon name="dish" :size="18"/>园区点餐</span><span><Icon name="reports" :size="18"/>餐饮运营</span><span><Icon name="agent" :size="18"/>智能助手</span></div></div><div class="story-plant"><svg viewBox="0 0 350 350" aria-hidden="true"><circle cx="190" cy="170" r="135" fill="#315e4b"/><path d="M164 318Q163 185 266 90M169 246Q83 175 90 100M181 202Q225 214 306 151M183 180Q142 113 158 63" stroke="#93b38b" stroke-width="3" fill="none"/><path d="M166 235Q65 251 85 136Q155 128 166 235M184 201Q224 116 306 151Q294 215 184 201M198 169Q206 75 282 74Q289 137 198 169M176 173Q111 98 161 38Q201 85 176 173" fill="#a4bd89" opacity=".7"/></svg></div><footer>园味 CAMPUS DINING <span>为每一天，做好每一餐。</span></footer></section>
  <section class="login-panel"><div class="login-card"><div class="login-kicker">WELCOME TO CAMPUS</div><h2>欢迎来到园味</h2><p class="muted">选择你的身份，开启今天的餐饮日常。</p><div class="segmented login-tabs"><button :class="{ active: role === 'admin' }" @click="role = 'admin'"><Icon name="store" :size="17"/>餐饮运营</button><button :class="{ active: role === 'user' }" @click="role = 'user'"><Icon name="users" :size="17"/>园区员工</button></div><form @submit.prevent="login"><label class="field"><span>{{ role === 'admin' ? '运营账号' : '员工账号' }}</span><input v-model="username" name="username" autocomplete="username" placeholder="请输入账号" required maxlength="50" /></label><label class="field"><span>登录密码</span><input v-model="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" required maxlength="100"/></label><div v-if="error" class="inline-error" role="alert"><Icon name="error" :size="17"/>{{ error }}</div><button type="submit" class="btn primary login-submit" :disabled="busy"><Icon v-if="busy" class="spin" name="loading" :size="18"/>{{ busy ? '正在登录…' : '进入' + (role === 'admin' ? '运营工作台' : '员工点餐') }}<Icon v-if="!busy" name="arrow" :size="18"/></button></form><div class="login-demo" v-if="API_MODE === 'mock'"><Icon name="eye" :size="19"/><div><strong>当前为前端演示模式</strong><p>账号管理员：admin / admin123<br>普通运营：operator / admin123<br>园区员工：staff / 123456</p><small>数据保存在本机浏览器，Agent 与支付为模拟。</small></div></div><div v-else class="login-demo"><Icon name="shield"/><div><strong>真实接口模式</strong><p>请使用你在后端实现的账号登录。</p></div></div><div class="login-footnote"><Icon name="leaf" :size="14"/>每一餐，都值得认真对待。</div></div></section></div>
</template>
