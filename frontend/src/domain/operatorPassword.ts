export function initialPasswordError(password: string, confirmation?: string): string {
  if (!password.trim()) return '请输入初始密码'
  if (password.length < 8 || password.length > 64) return '初始密码长度必须为 8～64 个字符'
  if (new TextEncoder().encode(password).length > 72) return '初始密码的 UTF-8 长度不能超过 72 字节，请减少中文或特殊字符'
  if (confirmation !== undefined && password !== confirmation) return '两次输入的密码不一致'
  return ''
}
