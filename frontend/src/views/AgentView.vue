<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { app, changeCart, refreshBase, safely, toast } from '@/stores/app'
import { money, statusLabels, statusColors, type AgentSession, type ChatMessage } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import FoodArt from '@/components/FoodArt.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute(); const router = useRouter(); const admin = computed(() => app.session?.role === 'admin'); const input = ref(String(route.query.prompt || '')); const messages = ref<ChatMessage[]>([]); const sessionId = ref(''); const busy = ref(false); const scrollArea = ref<HTMLElement | null>(null); const confirming = ref<ChatMessage | null>(null); const confirmBusy = ref(false)
const historyKey = `campus-chat-${API_MODE}-${app.session?.role}-${app.session?.id}`
const prompts = computed(() => admin.value ? [
  { icon: 'reports', text: '分析最近一周的营业额和菜品销量' }, { icon: 'orders', text: '查看最近的订单' },
  { icon: 'setmeal', text: '把元气鸡腿套餐下架' }, { icon: 'dish', text: '查询 30 元以内的套餐' }
] : [
  { icon: 'dish', text: '推荐 30 元以内、不辣的午餐套餐' }, { icon: 'leaf', text: '找一个清淡的素食套餐' },
  { icon: 'orders', text: '我的订单现在是什么状态' }, { icon: 'refresh', text: '我上次点的套餐，有更便宜的替代吗' }
])
const sessionList = ref<AgentSession[]>([])
async function loadSessions() {
  if (API_MODE !== 'api') return
  try { sessionList.value = (await api.agentSessions({ page: 1, pageSize: 8 })).records }
  catch { sessionList.value = [] }
}
async function openSession(id: string) {
  if (busy.value || id === sessionId.value) return
  busy.value = true
  try {
    const page = await api.agentMessages(id)
    messages.value = page.records.map(item => ({
      ...(item.reply || {}), id: item.id, role: item.role, content: item.content
    }))
    sessionId.value = id
    persist()
    await bottom()
  } catch (e) { toast(e instanceof Error ? e.message : '会话读取失败', 'error') }
  finally { busy.value = false }
}
async function removeSession(id: string) {
  if (busy.value) return
  try {
    await api.deleteAgentSession(id)
    if (id === sessionId.value) {
      messages.value = []; sessionId.value = ''; sessionStorage.removeItem(historyKey)
    }
    await loadSessions()
  } catch (e) { toast(e instanceof Error ? e.message : '删除会话失败', 'error') }
}
function persist() { sessionStorage.setItem(historyKey, JSON.stringify({ messages: messages.value.slice(-30), sessionId: sessionId.value })) }
async function bottom() { await nextTick(); if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight }
async function send(value?: string) {
  const text = (value || input.value).trim(); if (!text || busy.value) return
  if (text.length > 1000) { toast('单次消息请控制在 1000 字以内', 'error'); return }
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: text }); input.value = ''; busy.value = true; await bottom()
  try {
    const reply = await api.chat(text, sessionId.value)
    sessionId.value = reply.sessionId; messages.value.push({ ...reply, id: crypto.randomUUID(), role: 'assistant' })
    void loadSessions()
  } catch (e) { messages.value.push({ id: crypto.randomUUID(), role: 'assistant', content: e instanceof Error ? e.message : '助手暂时不可用，请重试。', traces: [{ name: 'request', state: 'failed', summary: '未获得成功结果' }] }) }
  finally { busy.value = false; persist(); await bottom() }
}
function keydown(event: KeyboardEvent) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void send() } }
async function clear() {
  if (busy.value) return
  const previous = sessionId.value
  messages.value = []; sessionId.value = ''; input.value = ''; sessionStorage.removeItem(historyKey)
  // 真实模式同时删除服务端会话，后端会让它下面待确认的写操作一起失效。
  if (API_MODE === 'api' && previous) {
    try { await api.deleteAgentSession(previous); await loadSessions() }
    catch (e) { toast(e instanceof Error ? e.message : '服务端会话删除失败', 'error') }
  }
}
async function confirm() {
  if (!confirming.value?.action || confirmBusy.value) return; confirmBusy.value = true
  try {
    const result = await api.confirmAction(confirming.value.action.id)
    confirming.value.actionState = 'confirmed'; confirming.value = null
    messages.value.push({ id: crypto.randomUUID(), role: 'assistant', content: result.content }); persist(); await bottom()
    await safely(refreshBase)
  } catch (e) { toast(e instanceof Error ? e.message : '确认失败', 'error') }
  finally { confirmBusy.value = false }
}
async function add(id: number) { await safely(() => changeCart(id, 1), '已加入购物车') }
async function decline(message: ChatMessage) {
  if (!message.action || confirmBusy.value) return
  confirmBusy.value = true
  await safely(async () => { await api.cancelAction(message.action!.id); message.actionState = 'cancelled'; persist() })
  confirmBusy.value = false
}
watch(() => route.query.prompt, prompt => { if (prompt) input.value = String(prompt) })
onMounted(async () => {
  try { const saved = JSON.parse(sessionStorage.getItem(historyKey) || 'null') as { messages: ChatMessage[]; sessionId: string } | null; if (saved?.messages) { messages.value = saved.messages; sessionId.value = saved.sessionId } } catch { /* ignore damaged history */ }
  await loadSessions()
  await bottom()
})
onBeforeUnmount(persist)
</script>
<template>
  <div class="agent-layout"><section class="panel agent-chat"><header class="chat-header"><div class="chat-avatar"><Icon name="agent" :size="21"/></div><div><h3>园味 · {{ admin ? '运营助手' : '点餐助手' }}</h3><p>{{ API_MODE === 'mock' ? '演示 Agent · 规则与模板响应，未调用 LLM' : '工具调用 Agent · 数据与执行由后端提供' }}</p></div><button class="icon-btn" aria-label="新建对话" title="新建对话" :disabled="busy" @click="clear"><Icon name="plus" :size="19"/></button></header><div ref="scrollArea" class="chat-messages"><div v-if="!messages.length" class="agent-welcome"><span class="welcome-icon"><Icon name="agent" :size="31"/></span><h2>{{ admin ? '今天，想了解些什么？' : '今天，想吃点什么？' }}</h2><p>{{ admin ? '查指标、看订单，或准备一次需要确认的运营操作。' : '告诉我预算和口味，帮你从可售菜单中找一顿午餐。' }}<br>回答依据业务数据，变更操作需要你的确认。</p><div class="prompt-grid"><button v-for="prompt in prompts" :key="prompt.text" class="prompt-card" :disabled="busy" @click="send(prompt.text)"><Icon :name="prompt.icon" :size="18"/><span>{{ prompt.text }}</span></button></div></div><div v-for="message in messages" :key="message.id" class="chat-message" :class="message.role"><span class="message-avatar"><Icon v-if="message.role === 'assistant'" name="agent" :size="17"/><template v-else>{{ app.session?.name.slice(0,1) }}</template></span><div class="message-main"><div class="message-role">{{ message.role === 'assistant' ? '园味助手' : '你' }}</div><div class="message-bubble">{{ message.content }}</div><div v-if="message.traces?.length" class="trace-list"><span v-for="(trace, index) in message.traces" :key="index" class="trace-chip" :title="trace.summary"><Icon :name="trace.state === 'success' ? 'check' : 'error'" :size="11"/>{{ trace.name }}</span></div><div v-if="message.items?.length" class="agent-products"><div v-for="item in message.items" :key="item.id" class="agent-product"><FoodArt :kind="item.art"/><div><h4>{{ item.name }}</h4><p>¥{{ money(item.price) }} · {{ item.tags.join(' / ') }}</p></div><button v-if="!admin" class="add-btn" :aria-label="'推荐加入购物车：' + item.name" @click="add(item.id)"><Icon name="plus" :size="15"/></button><button v-else class="icon-btn" aria-label="查看推荐商品" @click="router.push('/admin/' + (item.kind === 'setmeal' ? 'setmeals' : 'dishes') + '?q=' + encodeURIComponent(item.name))"><Icon name="right" :size="16"/></button></div></div><div v-for="order in message.orders || []" :key="order.id" class="agent-order"><div><strong class="order-id">{{ order.number }}</strong><p class="muted" style="font-size:10px;margin-top:3px">{{ order.lines[0]?.name }} · ¥{{ money(order.amount) }}</p></div><span class="badge" :class="statusColors[order.status]">{{ statusLabels[order.status] }}</span><button class="icon-btn" aria-label="查看助手返回的订单" @click="router.push((admin ? '/admin/orders' : '/user/orders') + '?detail=' + order.id)"><Icon name="right" :size="15"/></button></div><div v-if="message.metrics" class="inline-note" style="margin-top:10px"><Icon name="reports" :size="15"/><span>数据范围：{{ message.metrics.period.begin }} — {{ message.metrics.period.end }}<br>{{ message.metrics.metricNote }}</span></div><div v-if="message.action" class="action-card"><strong><Icon name="shield" :size="16"/>{{ message.action.label }}</strong><p>{{ message.action.detail }}</p><small>有效至 {{ new Date(message.action.expiresAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</small><div class="row-actions"><template v-if="!message.actionState"><button class="btn primary small" @click="confirming = message">确认执行</button><button class="btn small" :disabled="confirmBusy" @click="decline(message)">暂不执行</button></template><span v-else class="badge" :class="message.actionState === 'confirmed' ? 'green' : 'gray'">{{ message.actionState === 'confirmed' ? '已执行' : '已放弃本次操作' }}</span></div></div></div></div><div v-if="busy" class="chat-message"><span class="message-avatar"><Icon name="agent" :size="17"/></span><div class="message-bubble" style="display:flex;align-items:center;gap:9px"><Icon name="loading" class="spin" :size="15"/>正在获取业务结果…</div></div></div><form class="chat-input-area" @submit.prevent="send()"><div class="chat-input"><textarea v-model="input" :disabled="busy" maxlength="1000" rows="2" aria-label="给助手发送消息" :placeholder="admin ? '例如：分析最近一周营业额…' : '例如：推荐 30 元以内、不辣的套餐…'" @keydown="keydown"/><button class="btn primary" type="submit" aria-label="发送消息" :disabled="busy || !input.trim()"><Icon name="send" :size="17"/></button></div><div class="chat-input-note"><span>Enter 发送 · Shift + Enter 换行</span><span>{{ API_MODE === 'mock' ? '模拟响应，无模型费用' : '事实以工具返回为准' }}</span></div></form></section>
  <aside class="stack agent-sidebar">
    <section v-if="API_MODE === 'api'" class="panel agent-info-card">
      <h3><Icon name="clock" :size="17"/>最近会话</h3>
      <p class="agent-session-empty" v-if="!sessionList.length">暂无服务端会话；发送第一条消息后自动创建。</p>
      <ul v-else class="agent-session-list">
        <li v-for="item in sessionList" :key="item.id">
          <button class="btn text" :class="{ active: item.id === sessionId }" @click="openSession(item.id)">{{ item.title }}</button>
          <button class="icon-btn" :aria-label="'删除会话 ' + item.title" title="删除会话" :disabled="busy" @click="removeSession(item.id)"><Icon name="delete" :size="14"/></button>
        </li>
      </ul>
    </section>
    <section class="panel agent-info-card"><h3><Icon name="dish" :size="17"/>可用业务能力</h3><div class="tool-list"><div class="tool-list-item"><Icon name="search" :size="17"/><div><strong>菜品与套餐查询</strong><small>预算 · 口味 · 可售状态</small></div></div><div class="tool-list-item"><Icon name="orders" :size="17"/><div><strong>{{ admin ? '运营订单查询' : '我的订单查询' }}</strong><small>按照当前身份限定数据范围</small></div></div><div v-if="admin" class="tool-list-item"><Icon name="reports" :size="17"/><div><strong>经营指标与销量</strong><small>结构化数据驱动分析</small></div></div><div class="tool-list-item"><Icon name="shield" :size="17"/><div><strong>需要确认的操作</strong><small>{{ admin ? '订单取消 · 套餐下架' : '本人待支付订单取消' }}</small></div></div></div></section><section class="panel agent-info-card"><h3><Icon name="shield" :size="17"/>每一步，都有边界</h3><p>推荐只使用符合条件的候选商品。<br>写操作先展示影响，再由你确认。<br>用户身份和权限由后端决定。</p></section><section class="panel agent-info-card"><h3><Icon name="help" :size="17"/>{{ API_MODE === 'mock' ? '关于演示模式' : '关于真实接口' }}</h3><p>{{ API_MODE === 'mock' ? '这里展示完整的前端交互。查询、工具轨迹与确认都在浏览器按规则模拟，不调用 DeepSeek，也不写服务端数据库。刷新页面后，未执行的演示确认记录需要重新生成。' : 'DeepSeek 模型、工具循环、会话与确认状态由后端实现，密钥只留服务端。前端展示回答、工具轨迹、候选商品与确认卡片；模型不可用时明确报错，不会伪造回答。' }}</p><RouterLink to="/settings" class="text-link" style="margin-top:12px">查看接口与环境<Icon name="right" :size="12"/></RouterLink></section></aside></div>
  <ConfirmDialog v-if="confirming?.action" title="确认执行业务操作" :message="confirming.action.label + '\n' + confirming.action.detail + '\n后端执行前必须再次检查身份、有效期和业务状态。'" :busy="confirmBusy" confirm-label="确认执行" @close="!confirmBusy && (confirming = null)" @confirm="confirm"/>
</template>

<style scoped>
.agent-session-list { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 2px; }
.agent-session-list li { display: flex; align-items: center; gap: 2px; min-width: 0; }
.agent-session-list .btn { flex: 1; min-width: 0; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.agent-session-list .btn.active { font-weight: 600; color: #0876ba; }
.agent-session-empty { margin: 8px 0 0; font-size: 12px; color: #5d7287; }
</style>
