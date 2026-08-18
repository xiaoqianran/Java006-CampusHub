import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  clearTokens,
  jsonBody,
  refreshAccessToken,
  request,
  setAuthFailureHandler,
  setTokens
} from '@/api/client'
import { useAdminStore } from './admin'
import type { LoadOptions, LoginResponse, LoginUser, RegisterPayload, Role } from './types'

// 动态取 resource store，避免 auth ↔ resource 静态循环依赖。
async function resourceStore() {
  const { useResourceStore } = await import('./resource')
  return useResourceStore()
}

export const useAuthStore = defineStore('auth', () => {
  const role = ref<Role>((localStorage.getItem('shiqian_role') as Role) || 'student')
  const logged = ref(false)
  const initialized = ref(false)
  const currentUser = ref<LoginUser | null>(null)

  let currentUserLoadedAt = 0
  let currentUserInFlight: Promise<void> | null = null
  let restoreInFlight: Promise<void> | null = null

  const DATA_CACHE_TTL_MS = 30_000

  function isFresh(loadedAt?: number) {
    return Boolean(loadedAt && Date.now() - loadedAt < DATA_CACHE_TTL_MS)
  }

  function setRole(nextRole: Role) {
    role.value = nextRole
    localStorage.setItem('shiqian_role', nextRole)
  }

  async function clearLocalSession() {
    logged.value = false
    currentUser.value = null
    currentUserLoadedAt = 0
    setRole('student')
    const resource = await resourceStore()
    resource.clearSessionScopedState()
  }

  // token 刷新失败时同步清会话，避免 UI 仍显示已登录。
  setAuthFailureHandler(() => {
    void clearLocalSession()
  })

  async function loadCurrentUser(options: LoadOptions = {}) {
    if (!options.force && currentUser.value && isFresh(currentUserLoadedAt)) return
    if (currentUserInFlight) return currentUserInFlight

    const task = request<LoginUser>('/api/user/me')
      .then(user => {
        currentUser.value = user
        useAdminStore().upsertLocalUser(user)
        setRole(user.role === 'ADMIN' ? 'admin' : 'student')
        currentUserLoadedAt = Date.now()
      })
      .finally(() => {
        if (currentUserInFlight === task) currentUserInFlight = null
      })
    currentUserInFlight = task
    return task
  }

  /**
   * 新页面没有持久化 access token；启动时通过 HttpOnly refresh cookie 恢复一次会话。
   * 多个路由守卫共享同一个恢复 Promise，避免 refresh token 轮换竞争。
   */
  async function restoreSession() {
    if (initialized.value) return
    if (restoreInFlight) return restoreInFlight

    clearTokens() // 同时迁移清理旧版本 localStorage token。
    const task = (async () => {
      try {
        await refreshAccessToken()
        logged.value = true
        await loadCurrentUser({ force: true })
      } catch {
        clearTokens()
        await clearLocalSession()
      } finally {
        initialized.value = true
      }
    })()
    restoreInFlight = task
    try {
      await task
    } finally {
      if (restoreInFlight === task) restoreInFlight = null
    }
  }

  async function login(username: string, password: string) {
    const data = await request<LoginResponse>('/api/user/login', {
      method: 'POST',
      body: jsonBody({ username, password })
    })
    setTokens(data.accessToken)
    logged.value = true
    initialized.value = true
    currentUser.value = data
    setRole(data.role === 'ADMIN' ? 'admin' : 'student')
    currentUserLoadedAt = Date.now()
    const resource = await resourceStore()
    resource.invalidateAuthScopedCaches()
    // 登录响应不含邮箱/手机/头像等完整资料，立刻拉 /me 补齐会话资料。
    await Promise.allSettled([
      loadCurrentUser({ force: true }),
      resource.loadFavorites({}, { force: true }),
      resource.loadMyResources({}, { force: true })
    ])
  }

  async function refresh() {
    await refreshAccessToken()
    logged.value = true
  }

  async function register(payload: RegisterPayload) {
    await request<void>('/api/user/register', {
      method: 'POST',
      body: jsonBody(payload)
    })
    await login(payload.username, payload.password)
  }

  async function logout() {
    try {
      await request<void>('/api/user/logout', { method: 'POST' })
    } catch {
      // 网络或令牌已失效时仍清理本地内存态；服务端 Cookie 会在可达时清除。
    }
    clearTokens()
    initialized.value = true
    await clearLocalSession()
  }

  async function changePassword(payload: { oldPassword: string; newPassword: string }) {
    await request<void>('/api/user/me/password', {
      method: 'PUT',
      body: jsonBody(payload)
    })
    // 改密后后端使全部令牌失效并清 refresh cookie，本地必须退出。
    clearTokens()
    initialized.value = true
    await clearLocalSession()
  }

  async function updateProfile(payload: Partial<{ nickname: string; email: string; phone: string; avatar: string }>) {
    await request<void>('/api/user/me', {
      method: 'PUT',
      body: jsonBody(payload)
    })
    if (currentUser.value) {
      Object.assign(currentUser.value, payload)
    }
  }

  return {
    role,
    logged,
    initialized,
    currentUser,
    setRole,
    restoreSession,
    login,
    register,
    refresh,
    logout,
    changePassword,
    loadCurrentUser,
    updateProfile
  }
})
