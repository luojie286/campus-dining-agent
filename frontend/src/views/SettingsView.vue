<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { app, refreshBase, safely, toast } from '@/stores/app'
import { notifications } from '@/api/notifications'
import Icon from '@/components/Icon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const health = ref<{ javaVersion: string; springBootVersion: string; profiles: string[]; businessImplemented: boolean } | null>(null); const checking = ref(false); const healthError = ref(''); const reset = ref(false); const busy = ref(false)
async function checkHealth() {
  checking.value = true; healthError.value = ''
  try { const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000) }); if (!response.ok) throw new Error('后端环境未启动或健康接口不存在'); const json = await response.json(); if (json.code !== 1) throw new Error('环境检查未成功'); health.value = json.data }
  catch (e) { health.value = null; healthError.value = e instanceof Error ? e.message : '无法连接，请启动后端环境' }
  finally { checking.value = false }
}
async function copy(text: string) { try { await navigator.clipboard.writeText(text); toast('已复制') } catch { toast('复制失败，请手动选中复制', 'error') } }
async function resetDemo() { busy.value = true; await safely(async () => { await api.resetDemo(); await refreshBase(); reset.value = false }, '本机演示数据已恢复'); busy.value = false }
onMounted(checkHealth)
</script>
<template>
  <div class="settings-grid"><section class="panel"><div class="panel-head"><h2>运行模式</h2><span class="badge" :class="API_MODE === 'mock' ? 'amber' : 'green'">{{ API_MODE === 'mock' ? '前端演示' : '真实接口' }}</span></div><div class="settings-list"><div class="settings-row"><span>当前身份</span><strong>{{ app.session?.name }} · {{ app.session?.role === 'admin' ? '餐饮运营' : '园区员工' }}</strong></div><div class="settings-row"><span>前端工程</span><strong>Vue 3 · TypeScript · Vite</strong></div><div class="settings-row"><span>业务接口</span><strong>{{ API_MODE === 'mock' ? '浏览器本地演示数据' : '代理 /api → 127.0.0.1:8080' }}</strong></div><div class="settings-row"><span>Agent</span><strong>{{ API_MODE === 'mock' ? '规则与模板模拟，未调用 LLM' : '调用后端 /api/agent/chat' }}</strong></div><div class="settings-row"><span>订单通知</span><strong>{{ API_MODE === 'mock' ? '本地变化事件，未使用 WebSocket' : notifications.connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}</strong></div></div><div class="inline-note section-gap"><Icon name="help" :size="17"/><span>真实接口模式遇到错误会直接显示，不自动回退演示数据。切换模式后重启 Vite，并重新登录。</span></div></section><section class="panel"><div class="panel-head"><h2>后端环境</h2><button class="btn small" :disabled="checking" @click="checkHealth"><Icon name="refresh" :class="{ spin: checking }" :size="14"/>检查连接</button></div><template v-if="health"><div class="settings-row"><span>应用健康检查</span><span class="badge green">可连接</span></div><div class="settings-row"><span>Java</span><strong>{{ health.javaVersion }}</strong></div><div class="settings-row"><span>Spring Boot</span><strong>{{ health.springBootVersion }}</strong></div><div class="settings-row"><span>Profile</span><strong>{{ health.profiles.join(', ') }}</strong></div><div class="settings-row"><span>业务实现</span><span class="badge" :class="health.businessImplemented ? 'green' : 'gray'">{{ health.businessImplemented ? '以实际接口验证为准' : '环境骨架 · 等你实现' }}</span></div></template><div v-else class="inline-note"><Icon name="offline" :size="18"/><span>{{ checking ? '正在检查后端…' : healthError || '尚未检查' }}<br>前端演示可以独立运行，后端业务接口尚未实现。</span></div><p class="metric-detail">bootstrap 可以独立启动；local 使用你配置的 MySQL 与 Redis。基础依赖、目录和配置已经准备，业务与鉴权由你编写。</p></section><section class="panel"><div class="panel-head"><h2>接入你编写的后端</h2><Icon name="external" :size="18" style="color:#99aa84"/></div><ol class="help-list"><li>按 docs/API_CONTRACT.md 实现一个业务模块。</li><li>在 frontend/.env.local 中设置真实接口模式。</li><li>重启前端开发服务，并用真实账号登录。</li><li>逐个验证接口，再实现 Agent 与通知。</li></ol><div class="code-block">VITE_API_MODE=api
VITE_API_BASE=/api
VITE_WS_URL=ws://127.0.0.1:8080/ws/orders</div><button class="btn small" style="margin-top:13px" @click="copy('VITE_API_MODE=api\nVITE_API_BASE=/api\nVITE_WS_URL=ws://127.0.0.1:8080/ws/orders')">复制配置</button></section><section class="panel"><div class="panel-head"><h2>从哪一层开始写</h2><Icon name="sprout" :size="19" style="color:#91a478"/></div><div class="code-block" style="margin-top:0">backend/
  sky-common/   统一响应与公共基础
  sky-pojo/     Entity · DTO · VO（待写）
  sky-server/
    controller/  业务接口（待写）
    service/     业务规则（待写）
    mapper/      数据访问（待写）
    agent/       工具与模型循环（待写）</div><p class="metric-detail">推荐先实现登录与分类查询，再做菜品／套餐、购物车、订单，最后接 Agent。详细步骤见 README.md 和 docs/BACKEND_LEARNING.md。</p></section></div>
  <section v-if="API_MODE === 'mock' && app.session?.role === 'admin'" class="panel section-gap"><div class="panel-head" style="margin-bottom:0"><div><h2>恢复演示数据</h2><p>仅重置当前浏览器中的演示菜单、订单、地址和购物车，不影响数据库。</p></div><button class="btn danger" @click="reset = true"><Icon name="refresh" :size="15"/>恢复初始演示</button></div></section>
  <ConfirmDialog v-if="reset" title="恢复初始演示数据" message="你在本机演示中修改的菜单、订单、地址和购物车将恢复初始状态。其他项目与真实数据库不受影响。" danger :busy="busy" confirm-label="恢复演示" @close="!busy && (reset = false)" @confirm="resetDemo"/>
</template>
