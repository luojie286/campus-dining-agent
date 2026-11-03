import { expect, it } from 'vitest'
import { menuFormError } from './menuForm'
const item = { id: 0, kind: 'dish' as const, name: '菜品', categoryId: 1, price: 18.5,
  description: '', status: 0 as const, stock: 0, tags: [], art: 'chicken' as const, composition: [] }
it('允许金额边界与零库存', () => {
  expect(menuFormError({ ...item, price: 0.01 })).toBeNull()
  expect(menuFormError({ ...item, price: 9999 })).toBeNull()
})
it('空名称、非法分类、价格精度与初始库存校验', () => {
  expect(menuFormError({ ...item, name: ' ' })).toContain('名称')
  expect(menuFormError({ ...item, categoryId: 0 })).toContain('分类')
  expect(menuFormError({ ...item, price: 18.555 })).toContain('精确到分')
  expect(menuFormError({ ...item, stock: -1 })).toContain('初始库存')
})
it('所有辣度相互排斥', () => {
  expect(menuFormError({ ...item, tags: ['微辣', '辣'] })).toContain('辣度')
  expect(menuFormError({ ...item, tags: ['不辣', '辣'] })).toContain('辣度')
})

it('套餐组成必须非空、不重复、使用菜品偶数ID且数量合法', () => {
  const meal = { ...item, kind: 'setmeal' as const, composition: [{ dishId: 2, quantity: 1 }] }
  expect(menuFormError(meal)).toBeNull()
  expect(menuFormError({ ...meal, composition: [] })).toContain('1到100')
  expect(menuFormError({ ...meal, composition: [meal.composition[0]!, meal.composition[0]!] })).toContain('重复')
  expect(menuFormError({ ...meal, composition: [{ dishId: 3, quantity: 1 }] })).toContain('不能嵌套')
  expect(menuFormError({ ...meal, composition: [{ dishId: 2, quantity: 1.5 }] })).toContain('整数')
})
