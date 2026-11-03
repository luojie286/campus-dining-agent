import type { MenuItem } from './types'

/** 与后端菜品合同一致；编辑库存由独立接口负责，本表单不修改。 */
export function menuFormError(item: MenuItem): string | null {
  if (!item.name.trim() || item.name.trim().length > 50) return '商品名称必须为1到50个字符'
  if (!Number.isSafeInteger(item.categoryId) || item.categoryId <= 0) return '请选择商品分类'
  if (!Number.isFinite(item.price) || item.price < 0.01 || item.price > 9999) return '价格必须为0.01到9999元'
  if (Math.abs(item.price * 100 - Math.round(item.price * 100)) > 0.00000001) return '价格必须精确到分'
  if (!item.id && (!Number.isInteger(item.stock) || item.stock < 0 || item.stock > 2147483647)) return '初始库存必须为非负整数且不超过2147483647'
  if (item.description.length > 300) return '描述不能超过300个字符'
  if (item.status !== 0 && item.status !== 1) return '售卖状态不合法'
  const allowed = ['不辣', '微辣', '辣', '清淡', '素食', '高蛋白']
  if (item.tags.some(tag => !allowed.includes(tag)) || new Set(item.tags).size !== item.tags.length) return '标签不合法或重复'
  if (item.tags.filter(tag => ['不辣', '微辣', '辣'].includes(tag)).length > 1) return '辣度标签不能同时选择多个'
  if (item.kind === 'dish' && item.composition.length) return '菜品不能包含套餐组成'
  if (item.kind === 'setmeal') {
    if (!item.composition.length || item.composition.length > 100) return '套餐必须包含1到100种菜品'
    const ids = new Set<number>()
    for (const part of item.composition) {
      if (!Number.isSafeInteger(part.dishId) || part.dishId < 2 || part.dishId % 2 !== 0) return '请选择有效菜品，套餐不能嵌套套餐'
      if (ids.has(part.dishId)) return '套餐组成菜品不能重复'
      ids.add(part.dishId)
      if (!Number.isInteger(part.quantity) || part.quantity < 1 || part.quantity > 99) return '组成菜品份数必须为1到99的整数'
    }
  }
  return null
}
