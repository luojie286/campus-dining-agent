<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { API_MODE } from '@/api/http'
import { app, cartAmount, changeCart, refreshBase, safely, toast } from '@/stores/app'
import { money, type Address } from '@/domain/types'
import Icon from '@/components/Icon.vue'
import FoodArt from '@/components/FoodArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const router = useRouter(); const addresses = ref<Address[]>([]); const addressId = ref<number | undefined>(); const fulfillment = ref<'DELIVERY' | 'PICKUP'>('DELIVERY'); const remark = ref(''); const confirming = ref(false); const clearing = ref(false); const busy = ref(false); const loading = ref(true); const error = ref('')
const lines = computed(() => app.cart.map(line => ({ ...line, item: app.menu.find(x => x.id === line.itemId) })))
async function load() { error.value = ''; try { await refreshBase(); addresses.value = await api.addresses(); addressId.value = (addresses.value.find(x => x.isDefault) || addresses.value[0])?.id } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false } }
async function change(id: number, delta: number) { if (busy.value) return; busy.value = true; await safely(() => changeCart(id, delta)); busy.value = false }
async function clear() { busy.value = true; await safely(async () => { app.cart = await api.setCart([]); clearing.value = false }, '购物车已清空'); busy.value = false }
function review() { if (fulfillment.value === 'DELIVERY' && !addressId.value) { toast('请先添加或选择收餐地址', 'error'); return } confirming.value = true }
async function submit() {
  if (busy.value) return; busy.value = true
  try {
    const order = await api.submitOrder({ fulfillment: fulfillment.value, addressId: fulfillment.value === 'DELIVERY' ? addressId.value : undefined, remark: remark.value })
    confirming.value = false; app.cart = []; toast('订单已创建，请在订单页面完成支付')
    await router.push('/user/orders?detail=' + order.id)
  } catch (e) { toast(e instanceof Error ? e.message : '下单失败', 'error') }
  finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <div v-if="error" class="inline-error"><Icon name="error" :size="16"/>{{ error }}<button class="btn text" @click="load">重试</button></div><div v-if="loading" class="loading-state"><Icon name="loading" class="spin"/>准备购物车…</div>
  <div v-else-if="lines.length" class="cart-layout"><section class="panel"><div class="panel-head"><h2>这一餐，想吃这些</h2><button class="btn text" :disabled="busy" @click="clearing = true">清空购物车</button></div><div v-for="line in lines" :key="line.itemId" class="cart-line"><FoodArt :kind="line.item?.art || 'salad'"/><div class="cart-line-main"><h3>{{ line.item?.name || '商品暂不可售（编号 ' + line.itemId + '）' }}</h3><p>{{ line.item?.tags.join(' · ') || '请移除不可售商品后下单' }}</p><div class="quantity-control" style="margin-top:12px"><button aria-label="减少数量" :disabled="busy" @click="change(line.itemId, -1)"><Icon name="minus" :size="12"/></button><span>{{ line.quantity }}</span><button aria-label="增加数量" :disabled="busy || !line.item" @click="change(line.itemId, 1)"><Icon name="plus" :size="12"/></button></div></div><span class="price"><small>¥</small>{{ money((line.item?.price || 0) * line.quantity) }}</span><button class="icon-btn danger" :aria-label="'移除' + (line.item?.name || '商品')" :disabled="busy" @click="change(line.itemId, -line.quantity)"><Icon name="close" :size="16"/></button></div><RouterLink to="/user/menu" class="text-link" style="margin-top:22px"><Icon name="back" :size="14"/>继续逛菜单</RouterLink></section>
  <section class="panel checkout-summary"><div class="panel-head"><h2>订单确认</h2><Icon name="cart" :size="20" style="color:#8fa278"/></div><div class="field" role="group" aria-label="收餐方式"><span>收餐方式</span><div class="segmented" style="align-self:flex-start"><button :class="{ active: fulfillment === 'DELIVERY' }" @click="fulfillment = 'DELIVERY'"><Icon name="truck" :size="15"/>园区配送</button><button :class="{ active: fulfillment === 'PICKUP' }" @click="fulfillment = 'PICKUP'"><Icon name="store" :size="15"/>到店自提</button></div></div><label v-if="fulfillment === 'DELIVERY'" class="field"><span>收餐地址</span><select v-model="addressId" aria-label="选择收餐地址"><option :value="undefined">请选择地址</option><option v-for="a in addresses" :key="a.id" :value="a.id">{{ a.building }} · {{ a.detail }}（{{ a.consignee }}）</option></select><RouterLink to="/user/addresses" class="text-link"><Icon name="plus" :size="12"/>管理收餐地址</RouterLink></label><div v-else class="inline-note" style="margin-bottom:17px"><Icon name="address" :size="16"/>{{ app.shop?.pickupPoint }}</div><label class="field"><span>订单备注（选填）</span><textarea v-model="remark" maxlength="200" placeholder="例如：餐具一份。口味需求以商品信息为准。"/></label><div class="summary-row"><span>商品金额</span><span>¥{{ money(cartAmount) }}</span></div><div class="summary-row"><span>配送费用</span><span>园区配送免费</span></div><div class="summary-row total"><span>预计合计</span><strong>¥{{ money(cartAmount) }}</strong></div><button class="btn primary full" :disabled="busy || !app.shop?.open || lines.some(x => !x.item)" @click="review"><Icon name="check" :size="17"/>确认下单</button><p class="checkout-note">提交时{{ API_MODE === 'mock' ? '演示数据层' : '后端' }}将重新计算价格和检查库存。订单创建后 15 分钟内需完成支付，实际截止时间以订单返回值为准。</p><div v-if="API_MODE === 'mock'" class="demo-label" style="margin-top:12px"><Icon name="eye" :size="12"/>演示订单 · 不产生真实扣款</div></section></div>
  <section v-else class="panel"><EmptyState title="购物车还是空的" description="去看看今天的菜单，选一顿喜欢的午餐。" icon="cart"><RouterLink to="/user/menu" class="btn primary">去选午餐<Icon name="arrow" :size="15"/></RouterLink></EmptyState></section>
  <ConfirmDialog v-if="confirming" title="确认创建订单" :message="`收餐方式：${fulfillment === 'DELIVERY' ? '园区配送' : '到店自提'}\n预计金额：¥${money(cartAmount)}\n提交后创建待支付订单，${API_MODE === 'mock' ? '不会进行真实扣款' : '支付流程由后端确认'}。`" :busy="busy" confirm-label="提交订单" @close="!busy && (confirming = false)" @confirm="submit"/>
  <ConfirmDialog v-if="clearing" title="清空购物车" message="确定移除购物车中的全部商品？" :busy="busy" danger @close="!busy && (clearing = false)" @confirm="clear"/>
</template>

