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
  const logged = ref(Boolean(localStorage.getItem('shiqian_access_token')))
  const currentUser = ref<LoginUser | null>(null)

  let currentUserLoadedAt = 0
  let currentUserInFlight: Promise<void> | null = null

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

  async function login(username: string, password: string) {
    const data = await request<LoginResponse>('/api/user/login', {
      method: 'POST',
      body: jsonBody({ username, password })
    })
    setTokens(data.accessToken, data.refreshToken)
    logged.value = true
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
    // 显式刷新（request 层已自动处理 401 场景，此为可选手动调用）
    await refreshAccessToken()
  }

  async function register(payload: RegisterPayload) {
    await request<void>('/api/user/register', {
      method: 'POST',
      body: jsonBody(payload)
    })
    await login(payload.username, payload.password)
  }

  async function logout() {
    // 服务端撤销 access + 全部 refresh，避免本地清 token 后令牌仍可被盗用。
    try {
      await request<void>('/api/user/logout', { method: 'POST' })
    } catch {
      // 网络或已过期时仍清理本地态
    }
    clearTokens()
    await clearLocalSession()
  }

  async function changePassword(payload: { oldPassword: string; newPassword: string }) {
    await request<void>('/api/user/me/password', {
      method: 'PUT',
      body: jsonBody(payload)
    })
    // 改密后后端会使全部令牌失效，本地必须退出并要求重新登录。
    clearTokens()
    await clearLocalSession()
  }

  async function updateProfile(payload: Partial<{ nickname: string; email: string; phone: string; avatar: string }>) {
    await request<void>('/api/user/me', {
      method: 'PUT',
      body: jsonBody(payload)
    })
    // 刷新当前用户信息
    if (currentUser.value) {
      Object.assign(currentUser.value, payload)
    }
  }

  return {
    role,
    logged,
    currentUser,
    setRole,
    login,
    register,
    refresh,
    logout,
    changePassword,
    loadCurrentUser,
    updateProfile
  }
})
