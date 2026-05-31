/// <reference types="vite/client" />

interface Window {
  __SHIQIAN_CONFIG__?: {
    apiBaseUrl?: string
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
