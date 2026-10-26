import { describe, expect, it } from 'vitest'
import { initialPasswordError } from './operatorPassword'

describe('新增运营账号密码校验', () => {
  it('拒绝空白、过短、过长及两次密码不一致', () => {
    expect(initialPasswordError('        ')).toContain('请输入')
    expect(initialPasswordError('1234567')).toContain('8～64')
    expect(initialPasswordError('a'.repeat(65))).toContain('8～64')
    expect(initialPasswordError('password123', 'password124')).toContain('不一致')
    expect(initialPasswordError('password123', '')).toContain('不一致')
  })
  it('接受字符数边界和原始空格，按 UTF-8 字节限制中文密码', () => {
    expect(initialPasswordError('a'.repeat(8))).toBe('')
    expect(initialPasswordError('a'.repeat(64))).toBe('')
    expect(initialPasswordError('  password  ', '  password  ')).toBe('')
    expect(initialPasswordError('密'.repeat(24))).toBe('')
    expect(initialPasswordError('密'.repeat(25))).toContain('72 字节')
  })
})
