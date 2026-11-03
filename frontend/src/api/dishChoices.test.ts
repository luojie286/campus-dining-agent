import { expect, it, vi } from 'vitest'
import { loadDishChoices } from './dishChoices'
import type { MenuItem } from '@/domain/types'
const dish = (id: number): MenuItem => ({ id: id * 2, kind: 'dish', name: '菜品', categoryId: 1, price: 1, stock: 1, status: 1, tags: [], art: 'chicken', description: '', composition: [] })
it('读取后续页，包含第101条菜品', async () => {
  const first = Array.from({ length: 100 }, (_, i) => dish(i + 1))
  const fetch = vi.fn().mockResolvedValueOnce({ total: 101, records: first }).mockResolvedValueOnce({ total: 101, records: [dish(101)] })
  expect((await loadDishChoices(fetch)).length).toBe(101)
  expect(fetch.mock.calls).toEqual([[1], [2]])
})
it('拒绝不完整或变化的目录，接口失败保持明确错误', async () => {
  await expect(loadDishChoices(vi.fn().mockResolvedValue({ total: 2, records: [dish(1)] }))).rejects.toThrow('不完整')
  await expect(loadDishChoices(vi.fn().mockRejectedValue(new Error('数据库不可用')))).rejects.toThrow('数据库不可用')
  await expect(loadDishChoices(vi.fn().mockResolvedValue({ total: 10001, records: [] }))).rejects.toThrow('上限')
})
