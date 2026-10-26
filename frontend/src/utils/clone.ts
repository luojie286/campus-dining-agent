/** 数据模型均为 JSON 类型；通过 JSON 复制兼容 Vue 响应式 Proxy，避免编辑改动原记录。 */
export function cloneRecord<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
