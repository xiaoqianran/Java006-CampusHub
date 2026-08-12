/**
 * Pinia store 入口（按域拆分）。
 *
 * - auth: 登录会话 / 资料
 * - resource: 资源 CRUD、搜索、收藏、我的、回收站；含广场筛选 state
 * - catalog: 分类 / 标签
 * - admin: 用户管理、操作日志、内容安全
 * - ui: 主题等纯 UI
 * - types: 共享类型与 contentScene 常量
 */
export { useAuthStore } from './auth'
export { useResourceStore } from './resource'
export { useCatalogStore } from './catalog'
export { useAdminStore } from './admin'
export { useUiStore } from './ui'
export * from './types'
