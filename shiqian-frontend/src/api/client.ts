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

export function getAccessToken() {
  return localStorage.getItem('shiqian_access_token') || ''
}

export function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem('shiqian_access_token', accessToken)
  localStorage.setItem('shiqian_refresh_token', refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('shiqian_access_token')
  localStorage.removeItem('shiqian_refresh_token')
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
  const token = getAccessToken()
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(buildUrl(path, options.query), {
    ...options,
    headers
  })
  const result = await response.json().catch(() => null) as Result<T> | null

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
