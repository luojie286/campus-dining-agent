import { describe, expect, it } from 'vitest'
import { createSeed } from '@/api/seed'
import { availableQuantity, canTransition, inventoryRequirements } from './rules'

describe('演示库存与订单规则', () => {
  it('套餐可售量受组成菜品限制，停售组成使套餐不可售', () => {
    const { menu } = createSeed(); const meal = menu.find(x => x.id === 201)!
    menu.find(x => x.id === 101)!.stock = 2
    expect(availableQuantity(meal, menu)).toBe(2)
    menu.find(x => x.id === 101)!.status = 0
    expect(availableQuantity(meal, menu)).toBe(0)
  })
  it('合并单品与套餐的共享库存，避免分别检查导致超卖', () => {
    const { menu } = createSeed(); menu.find(x => x.id === 101)!.stock = 4
    expect(() => inventoryRequirements([{ itemId: 101, quantity: 3 }, { itemId: 201, quantity: 2 }], menu)).toThrow('共享库存不足')
  })
  it('拒绝负数、非整数数量，完成和取消都是终态', () => {
    const { menu } = createSeed()
    expect(() => inventoryRequirements([{ itemId: 101, quantity: -1 }], menu)).toThrow()
    expect(() => inventoryRequirements([{ itemId: 101, quantity: 1.5 }], menu)).toThrow()
    expect(canTransition('COMPLETED', 'CANCELLED')).toBe(false)
    expect(canTransition('CANCELLED', 'PREPARING')).toBe(false)
  })
})
