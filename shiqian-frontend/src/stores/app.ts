import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearTokens, jsonBody, request, setTokens, type PageResult } from '@/api/client'

export type Role = 'student' | 'admin'
export type ResourceStatus = '已发布' | '待审核' | '已驳回'

export interface ResourceApiItem {
  id: number
  userId: number
  title: string
  description?: string
  categoryId?: number
  fileUrl?: string
  fileSize?: number
  fileType?: string
  downloadCount?: number
  status: number
  createTime?: string
  updateTime?: string
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
  fileUrl?: string
  fileSize?: number
}

export interface UserItem {
  id: number
  name: string
  username?: string
  role: string
  email: string
  status: '正常' | '禁用'
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
  type: string
  desc: string
  fileUrl?: string
  fileSize?: number
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

  const categoryTree = ref<CategoryApiItem[]>([])
  const resources = ref<ResourceItem[]>([])
  const users = ref<UserItem[]>([])
  const favoriteIds = ref<number[]>([])
  const myResourceIds = ref<number[]>([])

  const flatCategories = computed(() => flattenCategories(categoryTree.value))
  const categories = computed(() => flatCategories.value.length ? flatCategories.value.map(item => item.name) : fallbackCategories)
  const publishedResources = computed(() => resources.value.filter(item => item.status === '已发布'))
  const pendingResources = computed(() => resources.value.filter(item => item.status === '待审核'))
  const favoriteResources = computed(() => resources.value.filter(item => favoriteIds.value.includes(item.id)))
  const myResources = computed(() => resources.value.filter(item => myResourceIds.value.includes(item.id)))

  const filteredResources = computed(() => {
    const text = keyword.value.trim()
    return publishedResources.value.filter(item => {
      const matchCategory = activeCategory.value === '全部分类' || item.cat === activeCategory.value
      const matchText = !text || `${item.title}${item.cat}${item.type}${item.desc}`.includes(text)
      return matchCategory && matchText
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
      author: item.userId ? `用户 ${item.userId}` : '匿名用户',
      userId: item.userId,
      views: 0,
      downloads: item.downloadCount || 0,
      favs: 0,
      status: mapStatus(item.status),
      desc: item.description || '',
      fileUrl: item.fileUrl,
      fileSize: item.fileSize
    }
  }

  function mapUser(item: LoginUser): UserItem {
    return {
      id: item.userId,
      username: item.username,
      name: item.nickname || item.username,
      role: item.role === 'ADMIN' ? '管理员' : '学生',
      email: item.email || '',
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
    await loadResources({ categoryId: categoryId(activeCategory.value), keyword: keyword.value })
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

  async function loadUsers() {
    const data = await request<PageResult<LoginUser>>('/api/user/admin/users', { query: { page: 1, size: 100 } })
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

  async function submitResource(payload: ResourceSubmitPayload) {
    const id = categoryId(payload.cat)
    if (!id) {
      throw new Error('请选择有效分类')
    }
    await request<void>('/api/resource', {
      method: 'POST',
      body: jsonBody({
        title: payload.title,
        description: payload.desc,
        categoryId: id,
        fileUrl: payload.fileUrl || 'https://example.com/demo-resource.pdf',
        fileSize: payload.fileSize || 0,
        fileType: payload.type
      })
    })
    await loadMyResources()
  }

  async function createCategory(name: string) {
    await request<void>('/api/category', {
      method: 'POST',
      body: jsonBody({ name, parentId: 0, sortOrder: categories.value.length + 1, status: 1 })
    })
    await loadCategories()
  }

  async function updateCategory(id: number, name: string) {
    const existing = flatCategories.value.find(item => item.id === id)
    await request<void>(`/api/category/${id}`, {
      method: 'PUT',
      body: jsonBody({ ...existing, name })
    })
    await loadCategories()
  }

  async function deleteCategory(id: number) {
    await request<void>(`/api/category/${id}`, { method: 'DELETE' })
    await loadCategories()
  }

  return {
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
    users,
    favoriteIds,
    myResourceIds,
    publishedResources,
    pendingResources,
    favoriteResources,
    myResources,
    filteredResources,
    setRole,
    login,
    register,
    logout,
    setCategory,
    resetFilters,
    loadHomeData,
    loadCategories,
    loadResources,
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
    approveResource,
    rejectResource,
    submitResource,
    createCategory,
    updateCategory,
    deleteCategory
  }
})
