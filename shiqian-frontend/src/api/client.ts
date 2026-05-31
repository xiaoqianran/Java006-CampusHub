export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

const runtimeConfig = window.__SHIQIAN_CONFIG__ || {}
const API_BASE = runtimeConfig.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || ''

export function buildApiUrl(path: string, query?: Record<string, unknown>) {
  if (/^https?:\/\//.test(path)) {
    return path
  }
  return buildUrl(path, query)
}

export function getAccessToken() {
  return localStorage.getItem('shiqian_access_token') || ''
}

export function getRefreshToken() {
  return localStorage.getItem('shiqian_refresh_token') || ''
}

export function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem('shiqian_access_token', accessToken)
  localStorage.setItem('shiqian_refresh_token', refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('shiqian_access_token')
  localStorage.removeItem('shiqian_refresh_token')
}

export async function refreshAccessToken(): Promise<{ accessToken: string; refreshToken: string }> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('无 refreshToken')
  }
  // 直接 fetch，避免循环依赖 request 自身
  const resp = await fetch(buildUrl('/api/user/refresh'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
  const result = await resp.json().catch(() => null) as Result<any> | null
  if (!resp.ok || !result || result.code !== 200 || !result.data) {
    throw new Error(result?.message || '刷新令牌失败')
  }
  const data = result.data as { accessToken: string; refreshToken: string }
  setTokens(data.accessToken, data.refreshToken)
  return data
}

function buildUrl(path: string, query?: Record<string, unknown>) {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })
  return /^https?:\/\//.test(API_BASE) ? url.href : url.pathname + url.search
}

export async function request<T>(path: string, options: RequestInit & { query?: Record<string, unknown> } = {}) {
  const doRequest = async (useToken: string): Promise<{ response: Response; result: Result<T> | null }> => {
    const headers = new Headers(options.headers)
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (useToken) {
      headers.set('Authorization', `Bearer ${useToken}`)
    }
    const response = await fetch(buildUrl(path, options.query), {
      ...options,
      headers
    })
    const result = await response.json().catch(() => null) as Result<T> | null
    return { response, result }
  }

  let token = getAccessToken()
  let { response, result } = await doRequest(token)

  // 401 时尝试用 refreshToken 刷新一次（非破坏性）
  const isAuthError = response.status === 401 || (result && result.code === 401)
  if (isAuthError && getRefreshToken()) {
    try {
      await refreshAccessToken()
      token = getAccessToken()
      const retry = await doRequest(token)
      response = retry.response
      result = retry.result
    } catch {
      clearTokens()
      throw new Error('登录已过期，请重新登录')
    }
  }

  if (!response.ok) {
    throw new Error(result?.message || `HTTP ${response.status}`)
  }
  if (!result) {
    throw new Error('接口返回为空')
  }
  if (result.code !== 200) {
    throw new Error(result.message || '操作失败')
  }
  return result.data
}

export function jsonBody(payload: unknown) {
  return JSON.stringify(payload)
}
