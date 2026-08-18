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
let accessToken = ''
let refreshInFlight: Promise<{ accessToken: string }> | null = null
let authFailureHandler: (() => void) | null = null

/** 由 Pinia store 注册：token 失效时同步清空登录态。 */
export function setAuthFailureHandler(handler: (() => void) | null) {
  authFailureHandler = handler
}

function notifyAuthFailure() {
  clearTokens()
  authFailureHandler?.()
}

export function buildApiUrl(path: string, query?: Record<string, unknown>) {
  if (/^https?:\/\//.test(path)) {
    return path
  }
  return buildUrl(path, query)
}

/** Access Token 只保存在当前页面内存中，不持久化到 Web Storage。 */
export function getAccessToken() {
  return accessToken
}

export function setTokens(nextAccessToken: string, _legacyRefreshToken?: string) {
  accessToken = nextAccessToken || ''
  // 清理旧版本遗留的长期令牌，升级后不再向 Web Storage 写入任何认证 token。
  localStorage.removeItem('shiqian_access_token')
  localStorage.removeItem('shiqian_refresh_token')
}

export function clearTokens() {
  accessToken = ''
  localStorage.removeItem('shiqian_access_token')
  localStorage.removeItem('shiqian_refresh_token')
}

/**
 * 使用 HttpOnly Cookie 中的 refresh token 轮换会话。
 * JavaScript 永远不读取 refresh token；并发 401 共享同一个刷新请求。
 */
export async function refreshAccessToken(): Promise<{ accessToken: string }> {
  if (refreshInFlight) return refreshInFlight

  const task = (async () => {
    const resp = await fetch(buildUrl('/api/user/refresh'), {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store'
    })
    const result = await resp.json().catch(() => null) as Result<any> | null
    if (!resp.ok || !result || result.code !== 200 || !result.data?.accessToken) {
      throw new Error(result?.message || '刷新令牌失败')
    }
    const data = result.data as { accessToken: string }
    setTokens(data.accessToken)
    return data
  })()
  refreshInFlight = task
  try {
    return await task
  } finally {
    if (refreshInFlight === task) refreshInFlight = null
  }
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

function isAuthErrorStatus(status: number, code?: number | null) {
  // 401：标准未认证；部分链路可能把未登录映射成 403 + 业务码 401
  return status === 401 || code === 401 || (status === 403 && code === 401)
}

function canAutoRefresh(path: string) {
  return !['/api/user/login', '/api/user/register', '/api/user/refresh'].includes(path)
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
      credentials: 'include',
      headers
    })
    const result = await response.json().catch(() => null) as Result<T> | null
    return { response, result }
  }

  let token = getAccessToken()
  let { response, result } = await doRequest(token)

  // Access Token 失效后仅尝试一次 HttpOnly Cookie 刷新；refresh 自身不会递归刷新。
  const isAuthError = isAuthErrorStatus(response.status, result?.code)
  if (isAuthError && canAutoRefresh(path)) {
    try {
      await refreshAccessToken()
      token = getAccessToken()
      const retry = await doRequest(token)
      response = retry.response
      result = retry.result
    } catch {
      notifyAuthFailure()
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

interface UploadRequestOptions {
  signal?: AbortSignal
  onProgress?: (percentage: number) => void
}

function uploadOnce<T>(
  path: string,
  body: FormData,
  token: string,
  options: UploadRequestOptions
): Promise<{ status: number; result: Result<T> | null }> {
  return new Promise((resolve, reject) => {
    if (options.signal?.aborted) {
      reject(new DOMException('上传已取消', 'AbortError'))
      return
    }

    const xhr = new XMLHttpRequest()
    const abort = () => xhr.abort()
    xhr.open('POST', buildUrl(path))
    xhr.withCredentials = true
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    xhr.upload.onprogress = event => {
      if (event.lengthComputable && event.total > 0) {
        options.onProgress?.(Math.round(event.loaded / event.total * 100))
      }
    }
    xhr.onload = () => {
      options.signal?.removeEventListener('abort', abort)
      let result: Result<T> | null = null
      try {
        result = JSON.parse(xhr.responseText) as Result<T>
      } catch {
        result = null
      }
      resolve({ status: xhr.status, result })
    }
    xhr.onerror = () => {
      options.signal?.removeEventListener('abort', abort)
      reject(new Error('上传网络异常'))
    }
    xhr.onabort = () => {
      options.signal?.removeEventListener('abort', abort)
      reject(new DOMException('上传已取消', 'AbortError'))
    }
    options.signal?.addEventListener('abort', abort, { once: true })
    xhr.send(body)
  })
}

export async function uploadRequest<T>(
  path: string,
  body: FormData,
  options: UploadRequestOptions = {}
) {
  let token = getAccessToken()
  let response = await uploadOnce<T>(path, body, token, options)
  const isAuthError = isAuthErrorStatus(response.status, response.result?.code)

  if (isAuthError && !options.signal?.aborted) {
    try {
      await refreshAccessToken()
      token = getAccessToken()
      options.onProgress?.(0)
      response = await uploadOnce<T>(path, body, token, options)
    } catch {
      notifyAuthFailure()
      throw new Error('登录已过期，请重新登录')
    }
  }

  if (response.status < 200 || response.status >= 300) {
    throw new Error(response.result?.message || `HTTP ${response.status}`)
  }
  if (!response.result) throw new Error('上传接口返回为空')
  if (response.result.code !== 200) {
    throw new Error(response.result.message || '上传失败')
  }
  return response.result.data
}

export function jsonBody(payload: unknown) {
  return JSON.stringify(payload)
}
