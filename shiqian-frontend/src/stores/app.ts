import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearTokens, jsonBody, refreshAccessToken, request, setTokens, type PageResult } from '@/api/client'

export type Role = 'student' | 'admin'
export type ResourceStatus = '已发布' | '待审核' | '已驳回'

export interface ResourceApiItem {
  id: number
  userId: number
  title: string
  // 旧字段（兼容历史数据）
  description?: string
  // 新字段（第一阶段主推）
  summary?: string
  contentMarkdown?: string
  contentType?: string
  categoryId?: number
  fileUrl?: string
  fileSize?: number
  fileType?: string
  downloadCount?: number
  status: number
  createTime?: string
  updateTime?: string
  attachments?: ResourceAttachmentItem[]
  authorNickname?: string   // 后端富化提供
}

export interface ResourceSearchItem {
  id: number
  userId: number
  title: string
  description?: string
  summary?: string
  contentMarkdown?: string
  categoryId?: number
  fileType?: string
  status: number
}

export interface UploadedFileItem {
  originalName: string
  fileUrl: string
  fileSize: number
  fileType: string
  mimeType?: string
  assetKind?: string
  usageType?: string
  sortOrder?: number
}

// 第二阶段：资源附件（用于详情展示）
export interface ResourceAttachmentItem {
  id?: number
  resourceId?: number
  fileName: string
  fileUrl: string
  fileSize: number
  fileType?: string
  mimeType?: string
  assetKind?: string
  usageType?: string
  sortOrder?: number
}

export interface CategoryApiItem {
  id: number
  parentId: number
  name: string
  sortOrder: number
  icon?: string
  status: number
  children?: CategoryApiItem[]
}

export interface LoginUser {
  userId: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
  email?: string
  phone?: string
  avatar?: string
  status?: number
}

export interface ResourceItem {
  id: number
  title: string
  cat: string
  categoryId?: number
  type: string
  author: string
  userId?: number
  views: number
  downloads: number
  favs: number
  status: ResourceStatus
  desc: string
  // 新字段（详情页渲染用）
  summary?: string
  contentMarkdown?: string
  contentType?: string
  fileUrl?: string
  fileSize?: number
  // 第二阶段：附件列表
  attachments?: ResourceAttachmentItem[]
}

export interface UserItem {
  id: number
  name: string
  username: string
  nickname: string
  email: string
  phone: string
  role: string
  status: '正常' | '禁用'
}

export interface AdminLogItem {
  id: number
  operatorId: number
  action: string
  targetId?: number
  detail?: string
  createTime?: string
}

interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

interface RegisterPayload {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
}

interface ResourceSubmitPayload {
  title: string
  cat: string
  summary: string
  contentMarkdown: string
  attachments?: UploadedFileItem[]
  files?: UploadedFileItem[]   // 临时兼容，submitResource 内部处理
}

const fallbackCategories = ['计算机科学', '高等数学', '大学英语', '考研资料', '课程笔记', '实验报告', '竞赛资料', '校园生活']

function mapStatus(status: number): ResourceStatus {
  if (status === 1) return '已发布'
  if (status === 2) return '已驳回'
  return '待审核'
}

function flattenCategories(items: CategoryApiItem[]): CategoryApiItem[] {
  return items.flatMap(item => [item, ...flattenCategories(item.children || [])])
}

export const useAppStore = defineStore('app', () => {
  const role = ref<Role>((localStorage.getItem('shiqian_role') as Role) || 'student')
  const logged = ref(Boolean(localStorage.getItem('shiqian_access_token')))
  const currentUser = ref<LoginUser | null>(null)
  const activeCategory = ref<string>('全部分类')
  const keyword = ref('')
  const loading = ref(false)

  // ===== 主题系统 (Search-First + Element Plus 暗色适配) =====
  const theme = ref<'light' | 'dark'>(
    (localStorage.getItem('shiqian_theme') as 'light' | 'dark' | null) || 'light'
  )
  const isDark = computed(() => theme.value === 'dark')

  function applyThemeToDOM(t: 'light' | 'dark') {
    const root = document.documentElement
    root.dataset.theme = t
    // 同步更新 body 背景，减少闪烁
    if (t === 'dark') {
      root.style.setProperty('color-scheme', 'dark')
    } else {
      root.style.setProperty('color-scheme', 'light')
    }
  }

  function initTheme() {
    const saved = localStorage.getItem('shiqian_theme') as 'light' | 'dark' | null
    const initial = saved || 'light'
    theme.value = initial
    applyThemeToDOM(initial)
  }

  function setTheme(t: 'light' | 'dark') {
    theme.value = t
    localStorage.setItem('shiqian_theme', t)
    applyThemeToDOM(t)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  const categoryTree = ref<CategoryApiItem[]>([])
  const resources = ref<ResourceItem[]>([])
  const recycleResources = ref<ResourceItem[]>([])
  const users = ref<UserItem[]>([])
  const adminLogs = ref<AdminLogItem[]>([])
  const favoriteIds = ref<number[]>([])
  const myResourceIds = ref<number[]>([])
  const searchResultIds = ref<number[] | null>(null)

  const flatCategories = computed(() => flattenCategories(categoryTree.value))
  const categories = computed(() => flatCategories.value.length ? flatCategories.value.map(item => item.name) : fallbackCategories)
  const publishedResources = computed(() => resources.value.filter(item => item.status === '已发布'))
  const pendingResources = computed(() => resources.value.filter(item => item.status === '待审核'))
  const rejectedResources = computed(() => resources.value.filter(item => item.status === '已驳回'))
  const reviewableResources = computed(() => resources.value.filter(item => item.status === '待审核' || item.status === '已驳回'))
  const favoriteResources = computed(() => resources.value.filter(item => favoriteIds.value.includes(item.id)))
  const myResources = computed(() => resources.value.filter(item => myResourceIds.value.includes(item.id)))

  const filteredResources = computed(() => {
    const text = keyword.value.trim()
    const source = searchResultIds.value
      ? publishedResources.value.filter(item => searchResultIds.value?.includes(item.id))
      : publishedResources.value
    return source.filter(item => {
      const matchCategory = activeCategory.value === '全部分类' || item.cat === activeCategory.value
      return matchCategory && (!text || searchResultIds.value || `${item.title}${item.cat}${item.type}${item.desc}`.includes(text))
    })
  })

  function categoryName(categoryId?: number) {
    return flatCategories.value.find(item => item.id === categoryId)?.name || '未分类'
  }

  function categoryId(category: string) {
    return flatCategories.value.find(item => item.name === category)?.id
  }

  function mapResource(item: ResourceApiItem): ResourceItem {
    return {
      id: item.id,
      title: item.title,
      cat: categoryName(item.categoryId),
      categoryId: item.categoryId,
      type: item.fileType || '资料',
      author: item.authorNickname || (item.userId ? `用户 ${item.userId}` : '匿名用户'),
      userId: item.userId,
      views: 0,
      downloads: item.downloadCount || 0,
      favs: 0,
      status: mapStatus(item.status),
      // 优先使用新字段 summary，兼容旧 description
      desc: item.summary || item.description || '',
      summary: item.summary,
      contentMarkdown: item.contentMarkdown,
      contentType: item.contentType,
      fileUrl: item.fileUrl,
      fileSize: item.fileSize,
      attachments: item.attachments || []
    }
  }

  function mapSearchItem(item: ResourceSearchItem): ResourceApiItem {
    return {
      id: item.id,
      title: item.title,
      userId: item.userId,
      description: item.description,
      summary: item.summary,
      contentMarkdown: item.contentMarkdown,
      categoryId: item.categoryId,
      fileType: item.fileType,
      status: item.status,
      downloadCount: 0
    }
  }

  function mapUser(item: LoginUser): UserItem {
    return {
      id: item.userId,
      username: item.username,
      nickname: item.nickname || '',
      name: item.nickname || item.username,
      role: item.role === 'ADMIN' ? '管理员' : '学生',
      email: item.email || '',
      phone: item.phone || '',
      status: item.status === 0 ? '禁用' : '正常'
    }
  }

  function mergeResources(items: ResourceApiItem[]) {
    const mapped = items.map(mapResource)
    const map = new Map(resources.value.map(item => [item.id, item]))
    mapped.forEach(item => map.set(item.id, item))
    resources.value = [...map.values()].sort((a, b) => b.id - a.id)
  }

  async function loadCategories() {
    categoryTree.value = await request<CategoryApiItem[]>('/api/category/tree')
  }

  async function loadResources(params: { page?: number, size?: number, categoryId?: number, keyword?: string } = {}) {
    const data = await request<PageResult<ResourceApiItem>>('/api/resource', {
      query: { page: params.page || 1, size: params.size || 100, categoryId: params.categoryId, keyword: params.keyword }
    })
    mergeResources(data.records)
  }

  async function loadRecycleResources(params: { page?: number, size?: number, keyword?: string } = {}) {
    const data = await request<PageResult<ResourceApiItem>>('/api/resource/recycle-bin', {
      query: { page: params.page || 1, size: params.size || 100, keyword: params.keyword }
    })
    recycleResources.value = data.records.map(mapResource)
  }

  async function loadHomeData() {
    loading.value = true
    try {
      await loadCategories()
      await loadResources()
      if (logged.value) {
        await Promise.allSettled([loadFavorites(), loadMyResources(), loadCurrentUser()])
      }
    } finally {
      loading.value = false
    }
  }

  async function searchResources() {
    const text = keyword.value.trim()
    if (!text) {
      searchResultIds.value = null
      await loadResources({ categoryId: categoryId(activeCategory.value) })
      return
    }

    const data = await request<PageResult<ResourceSearchItem>>('/api/resource/search', {
      query: { keyword: text, page: 1, size: 100 }
    })
    searchResultIds.value = data.records.map(item => item.id)
    mergeResources(data.records.map(mapSearchItem))

    await Promise.allSettled(data.records.map(item => loadResourceDetail(item.id)))
  }

  async function loadResourceDetail(id: number) {
    const data = await request<ResourceApiItem>(`/api/resource/${id}`)
    mergeResources([data])
    if (logged.value) {
      await refreshFavoriteState(id)
    }
    return getResource(id)
  }

  async function loadFavorites() {
    const data = await request<PageResult<ResourceApiItem>>('/api/resource/favorites', { query: { page: 1, size: 100 } })
    mergeResources(data.records)
    favoriteIds.value = data.records.map(item => item.id)
  }

  async function loadMyResources() {
    const data = await request<PageResult<ResourceApiItem>>('/api/resource/mine', { query: { page: 1, size: 100 } })
    mergeResources(data.records)
    myResourceIds.value = data.records.map(item => item.id)
  }

  async function loadUsers(params: { page?: number, size?: number, keyword?: string } = {}) {
    const data = await request<PageResult<LoginUser>>('/api/user/admin/users', {
      query: { page: params.page || 1, size: params.size || 100, keyword: params.keyword }
    })
    users.value = data.records.map(mapUser)
  }

  async function loadCurrentUser() {
    const user = await request<LoginUser>('/api/user/me')
    currentUser.value = user
    users.value = [mapUser(user), ...users.value.filter(item => item.id !== user.userId)]
    setRole(user.role === 'ADMIN' ? 'admin' : 'student')
  }

  function setRole(nextRole: Role) {
    role.value = nextRole
    localStorage.setItem('shiqian_role', nextRole)
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
    await Promise.allSettled([loadFavorites(), loadMyResources()])
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

  function logout() {
    clearTokens()
    logged.value = false
    currentUser.value = null
    favoriteIds.value = []
    myResourceIds.value = []
    setRole('student')
  }

  function setCategory(category: string) {
    activeCategory.value = category
  }

  async function resetFilters() {
    activeCategory.value = '全部分类'
    keyword.value = ''
    await loadResources()
  }

  function getResource(id: number) {
    return resources.value.find(item => item.id === id)
  }

  function isFavorite(id: number) {
    return favoriteIds.value.includes(id)
  }

  async function refreshFavoriteState(id: number) {
    const favored = await request<boolean>(`/api/resource/${id}/favorite`)
    favoriteIds.value = favored
      ? Array.from(new Set([...favoriteIds.value, id]))
      : favoriteIds.value.filter(item => item !== id)
  }

  async function toggleFavorite(id: number) {
    if (isFavorite(id)) {
      await request<void>(`/api/resource/${id}/favorite`, { method: 'DELETE' })
      favoriteIds.value = favoriteIds.value.filter(item => item !== id)
    } else {
      await request<void>(`/api/resource/${id}/favorite`, { method: 'POST' })
      favoriteIds.value = [...favoriteIds.value, id]
    }
  }

  async function downloadResource(id: number) {
    await request<void>(`/api/resource/${id}/download`, { method: 'POST' })
    const item = getResource(id)
    if (item) item.downloads += 1
  }

  async function removeMyResource(id: number) {
    await request<void>(`/api/resource/${id}`, { method: 'DELETE' })
    myResourceIds.value = myResourceIds.value.filter(item => item !== id)
    resources.value = resources.value.filter(item => item.id !== id)
  }

  async function removeResource(id: number) {
    await request<void>(`/api/resource/${id}`, { method: 'DELETE' })
    resources.value = resources.value.filter(item => item.id !== id)
    myResourceIds.value = myResourceIds.value.filter(item => item !== id)
    favoriteIds.value = favoriteIds.value.filter(item => item !== id)
    await loadRecycleResources()
  }

  async function takeDownResource(id: number) {
    await request<void>(`/api/resource/${id}/audit`, { method: 'PUT', query: { status: 2 } })
    const item = getResource(id)
    if (item) item.status = '已驳回'
  }

  async function approveResource(id: number) {
    await request<void>(`/api/resource/${id}/audit`, { method: 'PUT', query: { status: 1 } })
    const item = getResource(id)
    if (item) item.status = '已发布'
  }

  async function rejectResource(id: number) {
    await request<void>(`/api/resource/${id}/audit`, { method: 'PUT', query: { status: 2 } })
    const item = getResource(id)
    if (item) item.status = '已驳回'
  }

  async function resubmitResource(id: number) {
    await request<void>(`/api/resource/${id}/resubmit`, { method: 'PUT' })
    const item = getResource(id)
    if (item) item.status = '待审核'
  }

  async function uploadFiles(files: File[]) {
    const body = new FormData()
    files.forEach(file => body.append('files', file))
    return request<UploadedFileItem[]>('/api/resource/files', {
      method: 'POST',
      body
    })
  }

  async function submitResource(payload: ResourceSubmitPayload) {
    const categoryIdValue = categoryId(payload.cat)
    if (!categoryIdValue) {
      throw new Error('请选择有效分类')
    }

    const attachments = (payload.attachments ?? payload.files ?? []).map((file, index) => ({
      fileName: (file as any).originalName || (file as any).fileName,
      fileUrl: file.fileUrl,
      fileSize: file.fileSize,
      fileType: file.fileType,
      mimeType: file.mimeType || file.fileType || '',
      assetKind: (file as any).assetKind || 'FILE',
      usageType: (file as any).usageType || 'ATTACHMENT',
      sortOrder: (file as any).sortOrder ?? index
    }))

    const contentType = 'MARKDOWN'

    // 第二阶段：一个资源 + attachments 数组
    await request<void>('/api/resource', {
      method: 'POST',
      body: jsonBody({
        title: payload.title,
        categoryId: categoryIdValue,
        summary: payload.summary,
        contentMarkdown: payload.contentMarkdown,
        contentType,
        attachments
      })
    })

    await loadMyResources()
  }

  async function createCategory(name: string, icon?: string, sortOrder?: number) {
    // extended minimally to support icon (emoji/URL) and sortOrder from admin UI
    const nextSort = sortOrder ?? (flatCategories.value.length + 1)
    const payload: any = { name, parentId: 0, sortOrder: nextSort, status: 1 }
    if (icon) payload.icon = icon
    await request<void>('/api/category', {
      method: 'POST',
      body: jsonBody(payload)
    })
    await loadCategories()
  }

  async function updateCategory(id: number, name: string, icon?: string, sortOrder?: number) {
    // extended minimally to support icon and sortOrder editing from admin UI; 2-arg calls remain name-only
    const existing = flatCategories.value.find(item => item.id === id)
    const payload: any = { ...existing, name }
    if (icon !== undefined) payload.icon = icon || null
    if (sortOrder !== undefined) payload.sortOrder = sortOrder
    await request<void>(`/api/category/${id}`, {
      method: 'PUT',
      body: jsonBody(payload)
    })
    await loadCategories()
  }

  async function deleteCategory(id: number) {
    await request<void>(`/api/category/${id}`, { method: 'DELETE' })
    await loadCategories()
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

  // === 管理员用户管理 ===
  async function updateUserStatus(id: number, status: 0 | 1, keyword?: string) {
    await request<void>(`/api/user/admin/users/${id}/status`, {
      method: 'PUT',
      body: jsonBody({ status })
    })
    // 刷新用户列表（保留当前搜索关键词）
    await loadUsers({ keyword })
  }

  // === 回收站操作 ===
  async function restoreResource(id: number) {
    await request<void>(`/api/resource/${id}/restore`, { method: 'PUT' })
    // 从回收站列表移除，刷新其他列表
    recycleResources.value = recycleResources.value.filter(item => item.id !== id)
    await Promise.allSettled([loadResources(), loadMyResources()])
  }

  async function permanentDeleteResource(id: number) {
    await request<void>(`/api/resource/${id}/permanent`, { method: 'DELETE' })
    recycleResources.value = recycleResources.value.filter(item => item.id !== id)
  }

  // === 轻量管理员操作日志 ===
  async function loadAdminLogs(params: { page?: number, size?: number, action?: string } = {}) {
    const data = await request<PageResult<AdminLogItem>>('/api/admin/logs', {
      query: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        action: params.action || undefined
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

  return {
    // 主题
    theme,
    isDark,
    initTheme,
    setTheme,
    toggleTheme,
    // 原有
    role,
    logged,
    currentUser,
    activeCategory,
    keyword,
    loading,
    categoryTree,
    flatCategories,
    categories,
    resources,
    recycleResources,
    users,
    adminLogs,
    favoriteIds,
    myResourceIds,
    publishedResources,
    pendingResources,
    rejectedResources,
    reviewableResources,
    favoriteResources,
    myResources,
    filteredResources,
    setRole,
    login,
    register,
    refresh,
    logout,
    setCategory,
    resetFilters,
    loadHomeData,
    loadCategories,
    loadResources,
    loadRecycleResources,
    searchResources,
    loadResourceDetail,
    loadFavorites,
    loadMyResources,
    loadUsers,
    loadCurrentUser,
    getResource,
    isFavorite,
    refreshFavoriteState,
    toggleFavorite,
    downloadResource,
    removeMyResource,
    removeResource,
    takeDownResource,
    approveResource,
    rejectResource,
    resubmitResource,
    uploadFiles,
    submitResource,
    createCategory,
    updateCategory,
    deleteCategory,
    // 新增管理员能力
    updateUserStatus,
    restoreResource,
    permanentDeleteResource,
    updateProfile,
    // 轻量审计日志
    loadAdminLogs,
    recordAdminLog
  }
})
