import type { Session } from './types'

/** 只用于前端显示与导航；真实业务授权仍由后端执行。 */
export function canManageOperators(session: Session | null | undefined): boolean {
  return session?.role === 'admin' && session.accountRole === 'ACCOUNT_ADMIN'
}

export function sessionRoleLabel(session: Session | null | undefined): string {
  if (session?.role === 'user') return '园区员工'
  if (canManageOperators(session)) return '账号管理员'
  if (session?.accountRole === 'OPERATOR') return '普通运营'
  return '餐饮运营'
}
