import { describe, expect, it } from 'vitest'
import { canManageOperators, sessionRoleLabel } from './permissions'
import type { Session } from './types'

const admin: Session = { id: 1, name: '管理员', role: 'admin', token: 'test', accountRole: 'ACCOUNT_ADMIN' }

describe('运营账号管理入口', () => {
  it('只向运营端账号管理员开放，旧会话、未知角色和员工身份不获权', () => {
    expect(canManageOperators(admin)).toBe(true)
    expect(canManageOperators({ ...admin, accountRole: 'OPERATOR' })).toBe(false)
    expect(canManageOperators({ ...admin, accountRole: undefined })).toBe(false)
    expect(canManageOperators({ ...admin, accountRole: 'UNKNOWN' } as unknown as Session)).toBe(false)
    expect(canManageOperators({ ...admin, role: 'user' })).toBe(false)
    expect(canManageOperators(null)).toBe(false)
    expect(canManageOperators(undefined)).toBe(false)
  })

  it('个人信息区分账号管理员、普通运营和点餐员工', () => {
    expect(sessionRoleLabel(admin)).toBe('账号管理员')
    expect(sessionRoleLabel({ ...admin, accountRole: 'OPERATOR' })).toBe('普通运营')
    expect(sessionRoleLabel({ ...admin, role: 'user' })).toBe('园区员工')
    expect(sessionRoleLabel({ ...admin, accountRole: undefined })).toBe('餐饮运营')
  })
})
