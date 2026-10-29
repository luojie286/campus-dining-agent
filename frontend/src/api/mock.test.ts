import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Session } from '@/domain/types'

class MemoryStorage {
  values = new Map<string, string>()
  getItem(key: string) { return this.values.get(key) ?? null }
  setItem(key: string, value: string) { this.values.set(key, value) }
  removeItem(key: string) { this.values.delete(key) }
  clear() { this.values.clear() }
}
const admin: Session = { id: 1, name: '运营管理员', role: 'admin', token: 'demo-admin-1', accountRole: 'ACCOUNT_ADMIN' }
const user: Session = { id: 1001, name: '林同学', role: 'user', token: 'demo-user-1001' }
async function service(session: Session) {
  const http = await import('./http'); http.writeSession(session)
  return (await import('./mock')).mock
}
beforeEach(() => {
  vi.resetModules(); vi.stubGlobal('localStorage', new MemoryStorage()); vi.stubGlobal('window', new EventTarget())
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('演示数据层的关键行为', () => {
  it('商品详情返回副本，编辑保持库存，删除不存在记录按目标结果成功', async () => {
    const mock = await service(admin)
    const current = (await mock.menu('dish')).records[0]!
    const detail = await mock.menuItem('dish', current.id)
    detail.name = '本地表单未保存'
    expect((await mock.menuItem('dish', current.id)).name).toBe(current.name)
    expect((await mock.saveMenu({ ...current, stock: 999 })).stock).toBe(current.stock)
    await expect(mock.menuItem('dish', 999999)).rejects.toThrow('商品不存在')
    await expect(mock.deleteMenu(999999)).resolves.toBeUndefined()
  })
  it('普通运营可分页维护分类，停用不改商品状态，引用分类不能删除', async () => {
    const mock = await service({ id: 2, name: '运营', role: 'admin', token: 'demo-admin-2', accountRole: 'OPERATOR' })
    const created = await mock.saveCategory({ id: 0, name: ' 测试_% ', type: 2, sort: 0, status: 1 })
    const result = await mock.categoriesPage({ page: 1, pageSize: 1, name: '_%', type: 2, status: 1 })
    expect(result).toEqual({ total: 1, records: [{ ...created, name: '测试_%' }] })
    await expect(mock.saveCategory({ ...created, id: 0 })).rejects.toThrow('分类名称已存在')
    const before = (await mock.menu('dish', { pageSize: 100 })).records.map(item => item.status)
    await mock.setCategoryStatus(1, 0)
    expect((await mock.menu('dish', { pageSize: 100 })).records.map(item => item.status)).toEqual(before)
    await expect(mock.deleteCategory(1)).rejects.toThrow('分类关联商品')
    await mock.deleteCategory(created.id)
    expect(await mock.categoriesPage({ name: '_%' })).toEqual({ total: 0, records: [] })
  })
  it('详情读取最新资料，编辑资料不覆盖表单打开后变更的账号状态', async () => {
    let mock = await service(admin)
    const opened = await mock.operator(2)
    opened.name = '仅在表单中修改'
    expect((await mock.operator(2)).name).not.toBe(opened.name)
    await mock.setOperatorStatus(2, 0)
    const saved = await mock.updateOperator({ id: 2, name: ' 修改姓名 ', phone: opened.phone })
    expect(saved).toMatchObject({ id: 2, name: '修改姓名', username: opened.username, status: 0, accountRole: 'OPERATOR' })
    vi.resetModules()
    mock = await service(admin)
    expect(await mock.operator(2)).toEqual(saved)
    await mock.setOperatorStatus(2, 1)
    await expect(mock.login('admin', opened.username, 'admin123')).resolves.toMatchObject({ id: 2, name: '修改姓名' })
  })
  it('详情与编辑拒绝管理员目标、不存在的账号和非法资料', async () => {
    const mock = await service(admin)
    await expect(mock.operator(1)).rejects.toThrow('只能查询和编辑普通运营账号')
    await expect(mock.operator(99999)).rejects.toThrow('运营账号不存在')
    await expect(mock.updateOperator({ id: 1, name: '管理员', phone: '13800000000' })).rejects.toThrow('只能查询和编辑普通运营账号')
    await expect(mock.updateOperator({ id: 99999, name: '不存在', phone: '13800000000' })).rejects.toThrow('运营账号不存在')
    await expect(mock.updateOperator({ id: 2, name: ' ', phone: '13800000000' })).rejects.toThrow('姓名必须为1到30个字符')
    await expect(mock.updateOperator({ id: 2, name: '姓名', phone: '123' })).rejects.toThrow('手机号必须为11位大陆手机号')
    await service(user)
    await expect(mock.operator(2)).rejects.toThrow('当前身份无权执行此操作')
    await expect(mock.updateOperator({ id: 2, name: '姓名', phone: '13800000000' })).rejects.toThrow('当前身份无权执行此操作')
  })
  it('专用启停仅修改状态，重复设置成功，重载后保留，停用账号无法登录', async () => {
    let mock = await service(admin)
    const before = (await mock.operators())[0]!
    expect(await mock.setOperatorStatus(before.id, 0)).toEqual({ ...before, status: 0 })
    expect(await mock.setOperatorStatus(before.id, 0)).toEqual({ ...before, status: 0 })
    vi.resetModules(); mock = await service(admin)
    expect((await mock.operators())[0]!.status).toBe(0)
    await expect(mock.login('admin', before.username, 'admin123')).rejects.toThrow()
    expect(await mock.setOperatorStatus(before.id, 1)).toEqual(before)
    await expect(mock.login('admin', before.username, 'admin123')).resolves.toMatchObject({ id: before.id })
    await expect(mock.setOperatorStatus(1, 0)).rejects.toThrow('只能启用或停用普通运营账号')
    await expect(mock.setOperatorStatus(999999, 0)).rejects.toThrow('运营账号不存在')
  })
  it('普通运营不能通过专用启停接口越权修改自己或其他账号', async () => {
    const mock = await service({ id: 2, name: '普通运营', role: 'admin', token: 'demo-admin-2', accountRole: 'ACCOUNT_ADMIN' })
    await expect(mock.setOperatorStatus(2, 0)).rejects.toThrow('没有运营账号管理权限')
    await expect(mock.setOperatorStatus(1, 0)).rejects.toThrow('没有运营账号管理权限')
  })
  it('人员分页先筛选再倒序分页，保留完整总数、尾页和越界空页', async () => {
    const { createSeed } = await import('./seed')
    const data = createSeed()
    data.operators.push(...Array.from({ length: 35 }, (_, index) => ({
      id: index + 10, username: `page_${index + 1}`, name: `分页测试${index + 1}`,
      phone: '13800000003', status: (index + 1) % 5 === 0 ? 0 as const : 1 as const, accountRole: 'OPERATOR' as const
    })))
    localStorage.setItem('campus-demo-v1', JSON.stringify(data))
    const mock = await service(admin)
    const defaults = await mock.operatorsPage()
    expect(defaults.total).toBe(36)
    expect(defaults.records).toHaveLength(8)
    expect(defaults.records.every(x => x.accountRole === 'OPERATOR')).toBe(true)
    const first = await mock.operatorsPage({ name: ' 分页测试 ', pageSize: 10 })
    expect(first.total).toBe(35)
    expect(first.records.map(x => x.id)).toEqual([44, 43, 42, 41, 40, 39, 38, 37, 36, 35])
    expect((await mock.operatorsPage({ name: '分页测试', page: 4, pageSize: 10 })).records).toHaveLength(5)
    expect(await mock.operatorsPage({ name: '分页测试', page: 5, pageSize: 10 })).toEqual({ total: 35, records: [] })
    expect((await mock.operatorsPage({ name: '分页测试', status: 0 })).total).toBe(7)
    expect((await mock.operatorsPage({ name: '分页测试', status: 1 })).total).toBe(28)
    expect((await mock.operatorsPage({ name: 'PAGE_35' })).records[0]?.id).toBe(44)
    expect(await mock.operatorsPage({ name: '不存在' })).toEqual({ total: 0, records: [] })
    expect(await mock.operatorsPage({ name: '%' })).toEqual({ total: 0, records: [] })
    expect((await mock.operatorsPage({ name: '_' })).total).toBe(35)
    await expect(mock.operatorsPage({ pageSize: 101 })).rejects.toThrow('分页查询参数不合法')
    await expect(mock.operatorsPage({ page: 0 })).rejects.toThrow('分页查询参数不合法')
  })
  it('两类运营登录返回各自角色，管理员只管理普通运营账号', async () => {
    const mock = await service(admin)
    expect((await mock.login('admin', 'admin', 'admin123')).accountRole).toBe('ACCOUNT_ADMIN')
    expect((await mock.login('admin', 'operator', 'admin123')).accountRole).toBe('OPERATOR')
    const operators = await mock.operators()
    expect(operators.map(x => x.username)).toEqual(['operator'])
    const created = await mock.saveOperator({ id: 0, username: 'new-operator', name: '新运营', phone: '13800000003', status: 1, accountRole: 'ACCOUNT_ADMIN' }, 'NewPass123!')
    expect(created.accountRole).toBe('OPERATOR')
    await expect(mock.saveOperator({ id: 1, username: 'admin', name: '管理员', phone: '13800000000', status: 0 })).rejects.toThrow('只能管理普通运营')
  })

  it('新增账号使用自定义密码，重载和编辑资料后仍能登录，列表不携带密码', async () => {
    let mock = await service(admin)
    const password = '  customPass123!  '
    const created = await mock.saveOperator({ id: 0, username: 'custom', name: '自定义', phone: '13800000005', status: 1 }, password)
    expect(JSON.stringify(await mock.operators())).not.toContain(password)
    expect(localStorage.getItem('campus-demo-v1')).not.toContain(password)
    vi.resetModules(); mock = await service(admin)
    await mock.saveOperator({ ...created, name: '修改姓名' }, 'IgnoredPass123!')
    await expect(mock.login('admin', 'custom', password)).resolves.toMatchObject({ name: '修改姓名', accountRole: 'OPERATOR' })
    await expect(mock.login('admin', 'custom', password.trim())).rejects.toThrow('密码不正确')
    await expect(mock.login('admin', 'custom', 'admin123')).rejects.toThrow('密码不正确')
    await mock.saveOperator({ ...created, status: 0 })
    await expect(mock.login('admin', 'custom', password)).rejects.toThrow('已停用')
  })

  it('普通运营不能读取或修改账号，即使篡改会话角色也不获权；仍可管理菜单', async () => {
    const mock = await service({ id: 2, name: '食堂运营', role: 'admin', token: 'demo-admin-2', accountRole: 'ACCOUNT_ADMIN' })
    await expect(mock.operators()).rejects.toThrow('没有运营账号管理权限')
    await expect(mock.operatorsPage()).rejects.toThrow('没有运营账号管理权限')
    await expect(mock.saveOperator({ id: 0, username: 'test', name: '测试', phone: '13800000003', status: 1 })).rejects.toThrow('没有运营账号管理权限')
    const dishes = await mock.menu('dish')
    expect(dishes.records.length).toBeGreaterThan(0)
    await expect(mock.saveMenu({ ...dishes.records[0]!, description: '普通运营更新菜单' })).resolves.toMatchObject({ description: '普通运营更新菜单' })
  })

  it('旧演示数据补齐角色并保留原有菜单和账号状态', async () => {
    const { createSeed } = await import('./seed')
    const data = createSeed()
    data.operators.forEach(operator => { delete operator.accountRole })
    data.operators[1]!.status = 0
    data.menu[0]!.price = 19.5
    localStorage.setItem('campus-demo-v1', JSON.stringify(data))
    const mock = await service(admin)
    expect((await mock.login('admin', 'admin', 'admin123')).accountRole).toBe('ACCOUNT_ADMIN')
    expect((await mock.operators())[0]).toMatchObject({ username: 'operator', status: 0, accountRole: 'OPERATOR' })
    expect((await mock.menu('dish')).records[0]!.price).toBe(19.5)
  })

  it('员工只能查自己的订单，直接指定他人订单也被拒绝', async () => {
    const mock = await service(user); const own = await mock.orders({ pageSize: 100 })
    expect(own.records.every(x => x.userId === user.id)).toBe(true)
    const { createSeed } = await import('./seed'); const other = createSeed().orders.find(x => x.userId !== user.id)!
    await expect(mock.order(other.id)).rejects.toThrow('无权访问')
  })
  it('预算推荐不放宽条件，不返回下架商品；空候选如实返回', async () => {
    const mock = await service(user); const reply = await mock.chat('推荐 30 元以内、不辣的套餐', '')
    expect(reply.items!.length).toBeGreaterThan(0)
    expect(reply.items!.every(x => x.price <= 30 && x.status === 1 && x.tags.includes('不辣'))).toBe(true)
    const empty = await mock.chat('推荐 1 元以内的套餐', '')
    expect(empty.items).toEqual([]); expect(empty.content).toContain('没有找到')
  })
  it('写操作准备时不变更，确认后生效，重复确认不重复执行', async () => {
    const mock = await service(admin); const reply = await mock.chat('把元气鸡腿套餐下架', '')
    const item = () => mock.menu('setmeal', { name: '元气鸡腿' })
    expect((await item()).records[0]!.status).toBe(1)
    await mock.confirmAction(reply.action!.id)
    expect((await item()).records[0]!.status).toBe(0)
    expect((await mock.confirmAction(reply.action!.id)).content).toContain('未重复')
  })
  it('确认记录绑定身份，切换身份不能执行运营操作', async () => {
    const mock = await service(admin); const reply = await mock.chat('把元气鸡腿套餐下架', '')
    const http = await import('./http'); http.writeSession(user)
    await expect(mock.confirmAction(reply.action!.id)).rejects.toThrow('不属于当前身份')
  })
  it('确认过期后拒绝变更', async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-08T04:00:00Z'))
    const mock = await service(admin); const reply = await mock.chat('把元气鸡腿套餐下架', '')
    vi.advanceTimersByTime(6 * 60000)
    await expect(mock.confirmAction(reply.action!.id)).rejects.toThrow('过期')
    expect((await mock.menu('setmeal', { name: '元气鸡腿' })).records[0]!.status).toBe(1)
  })
  it('拒绝执行后确认记录失效，旧卡片不能再次写入', async () => {
    const mock = await service(admin); const reply = await mock.chat('把元气鸡腿套餐下架', '')
    await mock.cancelAction(reply.action!.id)
    await expect(mock.confirmAction(reply.action!.id)).rejects.toThrow()
    expect((await mock.menu('setmeal', { name: '元气鸡腿' })).records[0]!.status).toBe(1)
  })
  it('订单提交后释放购物车，取消只回补一次，并保留商品快照', async () => {
    const mock = await service(user); const before = (await mock.menu('dish', { pageSize: 100 })).records.find(x => x.id === 101)!.stock
    await mock.setCart([{ itemId: 201, quantity: 1 }]); const order = await mock.submitOrder({ fulfillment: 'PICKUP', remark: '' })
    expect(await mock.cart()).toEqual([])
    expect((await mock.menu('dish', { pageSize: 100 })).records.find(x => x.id === 101)!.stock).toBe(before - 1)
    await mock.orderAction(order.id, 'cancel')
    await expect(mock.orderAction(order.id, 'cancel')).rejects.toThrow()
    expect((await mock.menu('dish', { pageSize: 100 })).records.find(x => x.id === 101)!.stock).toBe(before)
    expect((await mock.order(order.id)).lines[0]!.name).toBe('元气鸡腿套餐')
  })
})
