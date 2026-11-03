import type { MenuItem, Page } from '@/domain/types'

/** 套餐编辑需要全部候选菜品，不能只取第一页或依赖合并菜单接口。 */
export async function loadDishChoices(fetchPage: (page: number) => Promise<Page<MenuItem>>): Promise<MenuItem[]> {
  const first = await fetchPage(1)
  if (!Number.isInteger(first.total) || first.total < 0 || first.total > 10000) {
    throw new Error('菜品数量异常或超过目录上限，请缩小目录后重试')
  }
  const records = [...first.records]
  for (let page = 2; page <= Math.ceil(first.total / 100); page++) {
    const next = await fetchPage(page)
    if (next.total !== first.total) throw new Error('菜品目录已变化，请重新加载')
    records.push(...next.records)
  }
  const unique = new Map(records.map(item => [item.id, item]))
  if (unique.size !== first.total || records.some(item => item.kind !== 'dish')) {
    throw new Error('菜品目录不完整，请重新加载')
  }
  return [...unique.values()]
}
