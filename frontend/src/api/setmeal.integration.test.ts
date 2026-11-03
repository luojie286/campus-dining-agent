import { readFileSync } from 'node:fs'
import { afterEach, expect, it, vi } from 'vitest'
import type { Category, MenuItem } from '@/domain/types'

const sessionFile = process.env.SETMEAL_INTEGRATION_SESSION
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
it.skipIf(!sessionFile)('真实HTTP：前端API经Vite代理完成套餐管理全流程并清理测试资源', async () => {
  vi.resetModules()
  vi.stubEnv('VITE_API_MODE', 'api')
  vi.stubEnv('VITE_API_BASE', process.env.SETMEAL_INTEGRATION_BASE!)
  const session = readFileSync(sessionFile!, 'utf8').trim()
  vi.stubGlobal('localStorage', { getItem: () => session, removeItem: () => undefined })
  vi.stubGlobal('window', new EventTarget())
  const { api } = await import('./index')
  const { loadDishChoices } = await import('./dishChoices')
  const prefix = process.env.SETMEAL_INTEGRATION_PREFIX!
  const categories: Category[] = []
  let dish: MenuItem | undefined
  let meal: MenuItem | undefined
  try {
    for (const type of [1, 2] as const) {
      categories.push(await api.saveCategory({ id: 0, name: `${prefix}分类${type}`, type, sort: 0, status: 1 }))
    }
    dish = await api.saveMenu({ id: 0, kind: 'dish', name: `${prefix}菜品`, categoryId: categories[0]!.id,
      price: 8, description: '', status: 1, stock: 12, tags: ['不辣'], art: 'chicken', composition: [] })
    const choices = await loadDishChoices(page => api.menu('dish', { page, pageSize: 100 }))
    expect(choices.some(item => item.id === dish!.id)).toBe(true)
    meal = await api.saveMenu({ id: 0, kind: 'setmeal', name: `${prefix}套餐`, categoryId: categories[1]!.id,
      price: 15.5, description: '真实HTTP联调临时记录', status: 0, stock: 5, tags: ['不辣'], art: 'chicken',
      composition: [{ dishId: dish.id, quantity: 2 }] })
    expect(meal.id % 2).toBe(1)
    await expect(api.saveMenu({ ...meal, id: 0 })).rejects.toThrow('名称已存在')
    const detail = await api.menuItem('setmeal', meal.id)
    expect(detail.composition).toEqual([{ dishId: dish.id, quantity: 2 }])
    meal = await api.saveMenu({ ...detail, name: `${prefix}套餐改`, stock: 999,
      composition: [{ dishId: dish.id, quantity: 3 }] })
    expect(meal.stock).toBe(5)
    const page = await api.menu('setmeal', { name: prefix, categoryId: categories[1]!.id, status: 0, pageSize: 1 })
    expect(page.total).toBe(1)
    expect(page.records[0]!.composition[0]!.quantity).toBe(3)
    expect((await api.menu('setmeal', { name: prefix, page: 2, pageSize: 1 })).records).toEqual([])
    // 传旧表单的名字也只修改目标状态。
    meal = await api.setMenuStatus({ ...meal, name: '过时表单' }, 1)
    expect(meal.name).toBe(`${prefix}套餐改`)
    await expect(api.deleteMenu(meal)).rejects.toThrow('停售')
    meal = await api.setMenuStatus(meal, 0)
    await api.deleteMenu(meal)
    await api.deleteMenu(meal)
    await expect(api.menuItem('setmeal', meal.id)).rejects.toThrow('不存在')
    expect((await api.menu('setmeal', { name: prefix })).total).toBe(0)
    meal = undefined
  } finally {
    // 仅清理本用例成功创建的资源；清理失败也应使测试失败。
    if (meal) { await api.setMenuStatus(meal, 0); await api.deleteMenu(meal) }
    if (dish) { await api.setMenuStatus(dish, 0); await api.deleteMenu(dish) }
    for (const category of categories.reverse()) await api.deleteCategory(category.id)
  }
}, 60000)
