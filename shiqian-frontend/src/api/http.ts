import axios, { AxiosError, type AxiosResponse } from 'axios';
import type { ApiResult } from '../types/api';

const TOKEN_KEY = 'shiqian_access_token';

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const token = window.localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  <T>(response: AxiosResponse<ApiResult<T>>) => response.data.data,
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status;
    const message = error.response?.data?.message ?? error.message ?? '请求失败';

    // 401 统一处理（后续路由守卫会接管登出）
    if (status === 401) {
      // 触发全局事件，App 或守卫监听
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }

    return Promise.reject(new Error(message));
  }
);

export function setAccessToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  window.localStorage.removeItem(TOKEN_KEY);
}
