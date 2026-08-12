import { defineStore } from 'pinia'
import { ref } from 'vue'
import { jsonBody, request, type PageResult } from '@/api/client'
import {
  mapUser,
  type AdminLogItem,
  type ContentReviewRecordItem,
  type LoginUser,
  type SensitiveWordItem,
  type UserItem
} from './types'

export const useAdminStore = defineStore('admin', () => {
  const users = ref<UserItem[]>([])
  const adminLogs = ref<AdminLogItem[]>([])

  function upsertLocalUser(user: LoginUser) {
    const mapped = mapUser(user)
    users.value = [mapped, ...users.value.filter(item => item.id !== user.userId)]
  }

  async function loadUsers(params: { page?: number, size?: number, keyword?: string } = {}) {
    const data = await request<PageResult<LoginUser>>('/api/user/admin/users', {
      query: { page: params.page || 1, size: params.size || 100, keyword: params.keyword }
    })
    users.value = data.records.map(mapUser)
  }

  async function updateUserStatus(id: number, status: 0 | 1, keyword?: string) {
    await request<void>(`/api/user/admin/users/${id}/status`, {
      method: 'PUT',
      body: jsonBody({ status })
    })
    // 刷新用户列表（保留当前搜索关键词）
    await loadUsers({ keyword })
  }

  async function updateUserRole(id: number, role: 'USER' | 'ADMIN', keyword?: string) {
    await request<void>(`/api/user/admin/users/${id}/role`, {
      method: 'PUT',
      body: jsonBody({ role })
    })
    // 刷新用户列表（保留当前搜索关键词）
    await loadUsers({ keyword })
  }

  // === 轻量管理员操作日志 ===
  async function loadAdminLogs(params: {
    page?: number
    size?: number
    action?: string
    operatorId?: number
    startTime?: string
    endTime?: string
  } = {}) {
    const data = await request<PageResult<AdminLogItem>>('/api/admin/logs', {
      query: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        action: params.action || undefined,
        operatorId: params.operatorId,
        startTime: params.startTime,
        endTime: params.endTime
      }
    })
    adminLogs.value = data.records || []
    return data
  }

  async function recordAdminLog(action: string, targetId?: number, detail?: string) {
    if (!action) return
    await request<void>('/api/admin/logs', {
      method: 'POST',
      body: jsonBody({ action, targetId, detail })
    })
  }

  async function loadSensitiveWords(keyword?: string) {
    return request<SensitiveWordItem[]>('/api/admin/content-moderation/sensitive-words', {
      query: { keyword: keyword || undefined }
    })
  }

  async function createSensitiveWord(payload: { word: string, level: number, status: number }) {
    return request<number>('/api/admin/content-moderation/sensitive-words', {
      method: 'POST',
      body: jsonBody(payload)
    })
  }

  async function updateSensitiveWord(id: number, payload: { word: string, level: number, status: number }) {
    await request<void>(`/api/admin/content-moderation/sensitive-words/${id}`, {
      method: 'PUT',
      body: jsonBody(payload)
    })
  }

  async function deleteSensitiveWord(id: number) {
    await request<void>(`/api/admin/content-moderation/sensitive-words/${id}`, {
      method: 'DELETE'
    })
  }

  async function reloadSensitiveWords() {
    await request<void>('/api/admin/content-moderation/sensitive-words/reload', {
      method: 'POST'
    })
  }

  async function loadContentReviewRecords(params: {
    page?: number
    size?: number
    reviewType?: string
    decision?: string
    resourceId?: number
  } = {}) {
    return request<PageResult<ContentReviewRecordItem>>('/api/admin/content-moderation/records', {
      query: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        reviewType: params.reviewType || undefined,
        decision: params.decision || undefined,
        resourceId: params.resourceId
      }
    })
  }

  return {
    users,
    adminLogs,
    upsertLocalUser,
    loadUsers,
    updateUserStatus,
    updateUserRole,
    loadAdminLogs,
    recordAdminLog,
    loadSensitiveWords,
    createSensitiveWord,
    updateSensitiveWord,
    deleteSensitiveWord,
    reloadSensitiveWords,
    loadContentReviewRecords
  }
})
