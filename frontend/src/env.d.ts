/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_MODE?: 'mock' | 'api'
  readonly VITE_API_BASE?: string
  readonly VITE_WS_URL?: string
}
