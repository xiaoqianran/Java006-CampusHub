import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearTokens, jsonBody, refreshAccessToken, request, setTokens, uploadRequest, type PageResult } from '@/api/client'

export type Role = 'student' | 'admin'
export type ResourceStatus = '已发布' | '待审核' | '待修改' | '已拒绝' | '已下架'

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
  viewCount?: number
  status: number
  reviewReason?: string
  reviewerId?: number
  reviewTime?: string
  offlineReason?: string
  publishedTime?: string
  createTime?: string
  updateTime?: string
  attachments?: ResourceAttachmentItem[]
  authorNickname?: string   // 后端富化提供
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
  reviewReason?: string
  reviewerId?: number
  reviewTime?: string
  offlineReason?: string
  publishedTime?: string
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
  contentMarkdown?: string
  attachments?: UploadedFileItem[]
  files?: UploadedFileItem[]   // 临时兼容，submitResource 内部处理
}

interface ResourceUpdatePayload {
  title: string
  cat: string
  summary: string
  contentMarkdown: string
  file?: UploadedFileItem | ResourceAttachmentItem
  attachments?: (UploadedFileItem | ResourceAttachmentItem)[]
}

interface LoadOptions {
  force?: boolean
}

interface HomeLoadOptions extends LoadOptions {
  includePersonal?: boolean
}

interface ResourceDetailLoadOptions extends LoadOptions {
  includeFavorite?: boolean
}

const DATA_CACHE_TTL_MS = 30_000

const fallbackCategories = ['计算机科学', '高等数学', '大学英语', '考研资料', '课程笔记', '实验报告', '竞赛资料', '校园生活']

function mapStatus(status: number): ResourceStatus {
  if (status === 1) return '已发布'
  if (status === 2) return '待修改'
  if (status === 3) return '已拒绝'
  if (status === 4) return '已下架'
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
  const sortMode = ref<'newest' | 'hottest'>('newest')
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

  let categoriesLoadedAt = 0
  let categoriesInFlight: Promise<void> | null = null
  const resourcesLoadedAt = new Map<string, number>()
  const resourcesInFlight = new Map<string, Promise<void>>()
  const detailLoadedAt = new Map<number, number>()
  const detailInFlight = new Map<number, Promise<void>>()
  const favoriteStateLoadedAt = new Map<number, number>()
  const favoriteStateInFlight = new Map<number, Promise<void>>()
  const favoritesLoadedAt = new Map<string, number>()
  const favoritesInFlight = new Map<string, Promise<void>>()
  const myResourcesLoadedAt = new Map<string, number>()
  const myResourcesInFlight = new Map<string, Promise<void>>()
  let currentUserLoadedAt = 0
  let currentUserInFlight: Promise<void> | null = null
  let homeDataInFlight: Promise<void> | null = null
  let coreDataLoaded = false
  let searchAbortController: AbortController | null = null
  let searchSequence = 0

  const flatCategories = computed(() => flattenCategories(categoryTree.value))
  const categories = computed(() => flatCategories.value.length ? flatCategories.value.map(item => item.name) : fallbackCategories)
  const publishedResources = computed(() => resources.value.filter(item => item.status === '已发布'))
  const pendingResources = computed(() => resources.value.filter(item => item.status === '待审核'))
  const hotResources = computed(() => [...publishedResources.value].sort((a, b) => (b.downloads || 0) - (a.downloads || 0)).slice(0, 6))
  const needsChangesResources = computed(() => resources.value.filter(item => item.status === '待修改'))
  const rejectedResources = computed(() => resources.value.filter(item => item.status === '已拒绝'))
  const offlineResources = computed(() => resources.value.filter(item => item.status === '已下架'))
  const reviewableResources = computed(() => resources.value.filter(item => item.status === '待审核'))
  const managedResources = computed(() => resources.value.filter(item => item.status === '已发布' || item.status === '已下架'))
  const favoriteResources = computed(() => {
    const list = resources.value.filter(item => favoriteIds.value.includes(item.id))
    return sortMode.value === 'hottest'
      ? [...list].sort((a, b) => ((b.downloads || 0) + (b.views || 0)) - ((a.downloads || 0) + (a.views || 0)) || b.id - a.id)
      : [...list].sort((a, b) => b.id - a.id)
  })
  const myResources = computed(() => {
    const list = resources.value.filter(item => myResourceIds.value.includes(item.id))
    return sortMode.value === 'hottest'
      ? [...list].sort((a, b) => ((b.downloads || 0) + (b.views || 0)) - ((a.downloads || 0) + (a.views || 0)) || b.id - a.id)
      : [...list].sort((a, b) => b.id - a.id)
  })

  const filteredResources = computed(() => {
    const text = keyword.value.trim()
    const source = searchResultIds.value
      ? publishedResources.value.filter(item => searchResultIds.value?.includes(item.id))
      : publishedResources.value
    const filtered = source.filter(item => {
      const matchCategory = activeCategory.value === '全部分类' || item.cat === activeCategory.value
      return matchCategory && (!text || searchResultIds.value || `${item.title}${item.cat}${item.type}${item.desc}`.includes(text))
    })
    // Preserve backend /search relevance order (ES multi-match score) when search active.
    // This is the key search UX fix: results now ranked by match quality (title^3 etc boosts).
    // Category post-filter preserves relative ranking. Non-search plaza browse + other views
    // (favorites/mine) continue using sortMode + client sort.
    if (searchResultIds.value) {
      const orderMap = new Map(searchResultIds.value.map((id, idx) => [id, idx]))
      return [...filtered].sort((a, b) => {
        const ia = orderMap.get(a.id) ?? Number.MAX_SAFE_INTEGER
        const ib = orderMap.get(b.id) ?? Number.MAX_SAFE_INTEGER
        return ia - ib
      })
    }
    // client-side sort (backend sort param passed but mergeResources preserves id order; reuse for Plaza UX)
    if (sortMode.value === 'hottest') {
      return [...filtered].sort((a, b) => ((b.downloads || 0) + (b.views || 0)) - ((a.downloads || 0) + (a.views || 0)) || b.id - a.id)
    }
    return [...filtered].sort((a, b) => b.id - a.id) // newest by id (proxy for create_time)
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
      // 优先使用后端富化 authorNickname；回退与后端保持一致（匿名用户），确保卡片/详情一致性
      author: item.authorNickname || '匿名用户',
      userId: item.userId,
      views: item.viewCount || 0,
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
      attachments: item.attachments || [],
      reviewReason: item.reviewReason,
      reviewerId: item.reviewerId,
      reviewTime: item.reviewTime,
      offlineReason: item.offlineReason,
      publishedTime: item.publishedTime
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
    mapped.forEach(item => {
      const existing = map.get(item.id)
      if (!existing) {
        map.set(item.id, item)
        return
      }
      const definedFields = Object.fromEntries(
        Object.entries(item).filter(([, value]) => value !== undefined)
      ) as Partial<ResourceItem>
      map.set(item.id, {
        ...existing,
        ...definedFields,
        attachments: item.attachments ?? existing.attachments
      })
    })
    resources.value = [...map.values()].sort((a, b) => b.id - a.id)
  }

  function isFresh(loadedAt?: number) {
    return Boolean(loadedAt && Date.now() - loadedAt < DATA_CACHE_TTL_MS)
  }

  function resourceRequestKey(params: { page?: number, size?: number, categoryId?: number, keyword?: string, sort?: string }) {
    return JSON.stringify({
      scope: `${logged.value}:${role.value}`,
      page: params.page ?? 1,
      size: params.size ?? 100,
      categoryId: params.categoryId ?? null,
      keyword: params.keyword?.trim() || '',
      sort: params.sort ?? sortMode.value
    })
  }

  function invalidateResourceCache(id?: number) {
    resourcesLoadedAt.clear()
    if (id !== undefined) {
      detailLoadedAt.delete(id)
      favoriteStateLoadedAt.delete(id)
    }
  }

  function invalidateCategoryCache() {
    categoriesLoadedAt = 0
  }

  async function loadCategories(options: LoadOptions = {}) {
    if (!options.force && isFresh(categoriesLoadedAt)) return
    if (categoriesInFlight) return categoriesInFlight

    const task = request<CategoryApiItem[]>('/api/category/tree')
      .then(data => {
        categoryTree.value = data
        categoriesLoadedAt = Date.now()
      })
      .finally(() => {
        if (categoriesInFlight === task) categoriesInFlight = null
      })
    categoriesInFlight = task
    return task
  }

  async function loadResources(
    params: { page?: number, size?: number, categoryId?: number, keyword?: string, sort?: string } = {},
    options: LoadOptions = {}
  ) {
    const key = resourceRequestKey(params)
    if (!options.force && isFresh(resourcesLoadedAt.get(key))) return
    const existingRequest = resourcesInFlight.get(key)
    if (existingRequest) return existingRequest

    const task = request<PageResult<ResourceApiItem>>('/api/resource', {
      query: {
        page: params.page ?? 1,
        size: params.size ?? 100,
        categoryId: params.categoryId,
        keyword: params.keyword,
        sort: params.sort ?? sortMode.value
      }
    })
      .then(data => {
        mergeResources(data.records)
        resourcesLoadedAt.set(key, Date.now())
      })
      .finally(() => {
        if (resourcesInFlight.get(key) === task) resourcesInFlight.delete(key)
      })
    resourcesInFlight.set(key, task)
    return task
  }

  async function loadRecycleResources(params: { page?: number, size?: number, keyword?: string } = {}) {
    const data = await request<PageResult<ResourceApiItem>>('/api/resource/recycle-bin', {
      query: { page: params.page || 1, size: params.size || 100, keyword: params.keyword }
    })
    recycleResources.value = data.records.map(mapResource)
  }

  async function loadHomeData(options: HomeLoadOptions = {}) {
    if (options.includePersonal && logged.value) {
      void Promise.allSettled([
        loadFavorites(),
        loadMyResources(),
        loadCurrentUser()
      ])
    } else if (logged.value && !currentUser.value) {
      void loadCurrentUser().catch(() => undefined)
    }

    if (!options.force && homeDataInFlight) return homeDataInFlight
    const hasUsableData = coreDataLoaded
    const task = (async () => {
      if (!hasUsableData) loading.value = true
      await Promise.all([
        loadCategories({ force: options.force }),
        loadResources({}, { force: options.force })
      ])
      coreDataLoaded = true
    })()
    homeDataInFlight = task

    try {
      await task
    } finally {
      if (!hasUsableData) loading.value = false
      if (homeDataInFlight === task) homeDataInFlight = null
    }
  }

  async function searchResources(params: { sort?: string } = {}) {
    searchAbortController?.abort()
    const sequence = ++searchSequence
    const text = keyword.value.trim()
    const sort = params.sort ?? sortMode.value
    if (!text) {
      searchResultIds.value = null
      await loadResources({ categoryId: categoryId(activeCategory.value), sort })
      return
    }

    const controller = new AbortController()
    searchAbortController = controller
    try {
      const data = await request<PageResult<ResourceApiItem>>('/api/resource/search', {
        query: { keyword: text, page: 1, size: 100, sort },
        signal: controller.signal
      })
      if (sequence !== searchSequence) return
      searchResultIds.value = data.records.map(item => item.id)
      mergeResources(data.records)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw error
    } finally {
      if (searchAbortController === controller) searchAbortController = null
    }
  }

  function cancelResourceSearch() {
    searchSequence += 1
    searchAbortController?.abort()
    searchAbortController = null
  }

  async function loadResourceDetail(id: number, options: ResourceDetailLoadOptions = {}) {
    if (options.force || !isFresh(detailLoadedAt.get(id))) {
      let task = detailInFlight.get(id)
      if (!task) {
        task = request<ResourceApiItem>(`/api/resource/${id}`)
          .then(data => {
            mergeResources([data])
            detailLoadedAt.set(id, Date.now())
          })
          .finally(() => {
            if (detailInFlight.get(id) === task) detailInFlight.delete(id)
          })
        detailInFlight.set(id, task)
      }
      await task
    }
    if (logged.value && options.includeFavorite !== false) {
      void refreshFavoriteState(id).catch(() => undefined)
    }
    return getResource(id)
  }

  async function loadFavorites(params: { sort?: string } = {}, options: LoadOptions = {}) {
    const sort = params.sort ?? sortMode.value
    const key = sort
    if (!options.force && isFresh(favoritesLoadedAt.get(key))) return
    const existingRequest = favoritesInFlight.get(key)
    if (existingRequest) return existingRequest

    const task = request<PageResult<ResourceApiItem>>('/api/resource/favorites', {
      query: { page: 1, size: 100, sort }
    })
      .then(data => {
        mergeResources(data.records)
        favoriteIds.value = data.records.map(item => item.id)
        favoritesLoadedAt.set(key, Date.now())
      })
      .finally(() => {
        if (favoritesInFlight.get(key) === task) favoritesInFlight.delete(key)
      })
    favoritesInFlight.set(key, task)
    return task
  }

  async function loadMyResources(params: { sort?: string } = {}, options: LoadOptions = {}) {
    const sort = params.sort ?? sortMode.value
    const key = sort
    if (!options.force && isFresh(myResourcesLoadedAt.get(key))) return
    const existingRequest = myResourcesInFlight.get(key)
    if (existingRequest) return existingRequest

    const task = request<PageResult<ResourceApiItem>>('/api/resource/mine', {
      query: { page: 1, size: 100, sort }
    })
      .then(data => {
        mergeResources(data.records)
        myResourceIds.value = data.records.map(item => item.id)
        myResourcesLoadedAt.set(key, Date.now())
      })
      .finally(() => {
        if (myResourcesInFlight.get(key) === task) myResourcesInFlight.delete(key)
      })
    myResourcesInFlight.set(key, task)
    return task
  }

  async function loadUsers(params: { page?: number, size?: number, keyword?: string } = {}) {
    const data = await request<PageResult<LoginUser>>('/api/user/admin/users', {
      query: { page: params.page || 1, size: params.size || 100, keyword: params.keyword }
    })
    users.value = data.records.map(mapUser)
  }

  async function loadCurrentUser(options: LoadOptions = {}) {
    if (!options.force && currentUser.value && isFresh(currentUserLoadedAt)) return
    if (currentUserInFlight) return currentUserInFlight

    const task = request<LoginUser>('/api/user/me')
      .then(user => {
        currentUser.value = user
        users.value = [mapUser(user), ...users.value.filter(item => item.id !== user.userId)]
        setRole(user.role === 'ADMIN' ? 'admin' : 'student')
        currentUserLoadedAt = Date.now()
      })
      .finally(() => {
        if (currentUserInFlight === task) currentUserInFlight = null
      })
    currentUserInFlight = task
    return task
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
    currentUserLoadedAt = Date.now()
    resourcesLoadedAt.clear()
    favoritesLoadedAt.clear()
    myResourcesLoadedAt.clear()
    await Promise.allSettled([
      loadFavorites({}, { force: true }),
      loadMyResources({}, { force: true })
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

  function logout() {
    clearTokens()
    logged.value = false
    currentUser.value = null
    favoriteIds.value = []
    myResourceIds.value = []
    currentUserLoadedAt = 0
    resourcesLoadedAt.clear()
    favoritesLoadedAt.clear()
    myResourcesLoadedAt.clear()
    favoriteStateLoadedAt.clear()
    setRole('student')
  }

  function setCategory(category: string) {
    activeCategory.value = category
  }

  async function resetFilters() {
    activeCategory.value = '全部分类'
    keyword.value = ''
    sortMode.value = 'newest'
    await loadResources()
  }

  function getResource(id: number) {
    return resources.value.find(item => item.id === id)
  }

  function isFavorite(id: number) {
    return favoriteIds.value.includes(id)
  }

  async function refreshFavoriteState(id: number, options: LoadOptions = {}) {
    if (!options.force && isFresh(favoriteStateLoadedAt.get(id))) return
    const existingRequest = favoriteStateInFlight.get(id)
    if (existingRequest) return existingRequest

    const task = request<boolean>(`/api/resource/${id}/favorite`)
      .then(favored => {
        favoriteIds.value = favored
          ? Array.from(new Set([...favoriteIds.value, id]))
          : favoriteIds.value.filter(item => item !== id)
        favoriteStateLoadedAt.set(id, Date.now())
      })
      .finally(() => {
        if (favoriteStateInFlight.get(id) === task) favoriteStateInFlight.delete(id)
      })
    favoriteStateInFlight.set(id, task)
    return task
  }

  async function toggleFavorite(id: number) {
    if (isFavorite(id)) {
      await request<void>(`/api/resource/${id}/favorite`, { method: 'DELETE' })
      favoriteIds.value = favoriteIds.value.filter(item => item !== id)
    } else {
      await request<void>(`/api/resource/${id}/favorite`, { method: 'POST' })
      favoriteIds.value = [...favoriteIds.value, id]
    }
    favoriteStateLoadedAt.set(id, Date.now())
    favoritesLoadedAt.clear()
  }

  async function downloadResource(id: number) {
    const vo = await request<any>(`/api/resource/${id}/download`, { method: 'POST' })
    const item = getResource(id)
    if (item) item.downloads += 1
    return vo
  }

  async function incrementView(id: number) {
    // 匿名友好：详情页加载后调用，不阻塞UI，乐观更新本地
    try {
      await request<void>(`/api/resource/${id}/view`, { method: 'POST' })
      const item = getResource(id)
      if (item) item.views += 1
    } catch {
      // 静默失败，不影响详情展示（计数为最佳努力）
    }
  }

  async function removeMyResource(id: number) {
    await request<void>(`/api/resource/${id}`, { method: 'DELETE' })
    myResourceIds.value = myResourceIds.value.filter(item => item !== id)
    resources.value = resources.value.filter(item => item.id !== id)
    invalidateResourceCache(id)
    myResourcesLoadedAt.clear()
  }

  async function removeResource(id: number) {
    await request<void>(`/api/resource/${id}`, { method: 'DELETE' })
    resources.value = resources.value.filter(item => item.id !== id)
    myResourceIds.value = myResourceIds.value.filter(item => item !== id)
    favoriteIds.value = favoriteIds.value.filter(item => item !== id)
    invalidateResourceCache(id)
    myResourcesLoadedAt.clear()
    favoritesLoadedAt.clear()
    await loadRecycleResources()
  }

  async function reviewResource(id: number, status: 1 | 2 | 3 | 4, reason?: string) {
    await request<void>(`/api/resource/${id}/audit`, {
      method: 'PUT',
      body: jsonBody({ status, reason: reason?.trim() || undefined })
    })
    const item = getResource(id)
    if (item) {
      item.status = mapStatus(status)
      item.reviewReason = status === 2 || status === 3 ? reason?.trim() : undefined
      item.offlineReason = status === 4 ? reason?.trim() : undefined
      item.reviewTime = new Date().toISOString()
    }
    invalidateResourceCache(id)
  }

  async function takeDownResource(id: number, reason: string) {
    await reviewResource(id, 4, reason)
  }

  async function approveResource(id: number) {
    await reviewResource(id, 1)
  }

  async function requestResourceChanges(id: number, reason: string) {
    await reviewResource(id, 2, reason)
  }

  async function rejectResource(id: number, reason: string) {
    await reviewResource(id, 3, reason)
  }

  async function resubmitResource(id: number) {
    await request<void>(`/api/resource/${id}/resubmit`, { method: 'PUT' })
    const item = getResource(id)
    if (item) item.status = '待审核'
    invalidateResourceCache(id)
    myResourcesLoadedAt.clear()
  }

  async function uploadFiles(
    files: File[],
    options: { signal?: AbortSignal, onProgress?: (percentage: number) => void } = {}
  ) {
    const body = new FormData()
    files.forEach(file => body.append('files', file))
    return uploadRequest<UploadedFileItem[]>('/api/resource/files', body, options)
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

    const contentMarkdown = payload.contentMarkdown?.trim() || ''
    const contentType = attachments.length && contentMarkdown
      ? 'MIXED'
      : attachments.length
        ? 'FILE'
        : 'ARTICLE'

    // 第二阶段：一个资源 + attachments 数组
    await request<void>('/api/resource', {
      method: 'POST',
      body: jsonBody({
        title: payload.title,
        categoryId: categoryIdValue,
        summary: payload.summary,
        contentMarkdown: contentMarkdown || null,
        contentType,
        attachments
      })
    })

    invalidateResourceCache()
    myResourcesLoadedAt.clear()
    await loadMyResources({}, { force: true })
  }

  async function updateResource(id: number, payload: ResourceUpdatePayload) {
    const categoryIdValue = categoryId(payload.cat)
    if (!categoryIdValue) {
      throw new Error('请选择有效分类')
    }

    const existing = getResource(id)
    const hasFiles = payload.attachments !== undefined
      ? payload.attachments.length > 0
      : Boolean(payload.file || existing?.attachments?.length || existing?.fileUrl)
    const hasText = Boolean(payload.contentMarkdown.trim())

    const body: any = {
      title: payload.title,
      categoryId: categoryIdValue,
      summary: payload.summary,
      description: payload.summary,
      contentMarkdown: payload.contentMarkdown,
      contentType: hasFiles && hasText ? 'MIXED' : hasFiles ? 'FILE' : 'ARTICLE'
    }

    // 如果提供 attachments 数组（编辑多附件场景），则发送之（后端将替换）；否则兼容 legacy file
    if (payload.attachments) {
      const attachments = payload.attachments.map((file, index) => ({
        fileName: (file as any).originalName || (file as any).fileName,
        fileUrl: file.fileUrl,
        fileSize: file.fileSize,
        fileType: file.fileType,
        mimeType: file.mimeType || file.fileType || '',
        assetKind: (file as any).assetKind || 'FILE',
        usageType: (file as any).usageType || 'ATTACHMENT',
        sortOrder: (file as any).sortOrder ?? index
      }))
      body.attachments = attachments
    } else if (payload.file) {
      body.fileUrl = payload.file.fileUrl
      body.fileSize = payload.file.fileSize
      body.fileType = payload.file.fileType
    }

    await request<void>(`/api/resource/${id}`, {
      method: 'PUT',
      body: jsonBody(body)
    })

    const item = getResource(id)
    if (item) {
      item.title = payload.title
      item.cat = payload.cat
      item.categoryId = categoryIdValue
      item.desc = payload.summary
      item.summary = payload.summary
      item.contentMarkdown = payload.contentMarkdown
      if (payload.attachments) {
        item.attachments = payload.attachments.map((f, index) => ({
          fileName: (f as any).originalName || (f as any).fileName || '',
          fileUrl: f.fileUrl,
          fileSize: f.fileSize || 0,
          fileType: f.fileType,
          mimeType: f.mimeType,
          assetKind: (f as any).assetKind,
          usageType: (f as any).usageType,
          sortOrder: (f as any).sortOrder ?? index
        })) as any
        // 兼容：若有附件，更新 type 为第一个
        if (item.attachments && item.attachments.length > 0) {
          item.type = item.attachments[0].fileType || item.type
        }
      } else if (payload.file) {
        item.fileUrl = payload.file.fileUrl
        item.fileSize = payload.file.fileSize
        item.type = payload.file.fileType || item.type
      }
    }
    invalidateResourceCache(id)
    detailLoadedAt.set(id, Date.now())
    myResourcesLoadedAt.clear()
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
    invalidateCategoryCache()
    await loadCategories({ force: true })
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
    invalidateCategoryCache()
    await loadCategories({ force: true })
  }

  async function deleteCategory(id: number) {
    await request<void>(`/api/category/${id}`, { method: 'DELETE' })
    invalidateCategoryCache()
    await loadCategories({ force: true })
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

  async function updateUserRole(id: number, role: 'USER' | 'ADMIN', keyword?: string) {
    await request<void>(`/api/user/admin/users/${id}/role`, {
      method: 'PUT',
      body: jsonBody({ role })
    })
    // 刷新用户列表（保留当前搜索关键词）
    await loadUsers({ keyword })
  }

  // === 回收站操作 ===
  async function restoreResource(id: number) {
    await request<void>(`/api/resource/${id}/restore`, { method: 'PUT' })
    // 从回收站列表移除，刷新其他列表
    recycleResources.value = recycleResources.value.filter(item => item.id !== id)
    invalidateResourceCache(id)
    myResourcesLoadedAt.clear()
    await Promise.allSettled([
      loadResources({}, { force: true }),
      loadMyResources({}, { force: true })
    ])
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
    sortMode,
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
    needsChangesResources,
    offlineResources,
    managedResources,
    hotResources,
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
    cancelResourceSearch,
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
    incrementView,
    removeMyResource,
    removeResource,
    takeDownResource,
    approveResource,
    requestResourceChanges,
    rejectResource,
    reviewResource,
    resubmitResource,
    uploadFiles,
    submitResource,
    updateResource,
    createCategory,
    updateCategory,
    deleteCategory,
    // 新增管理员能力
    updateUserStatus,
    updateUserRole,
    restoreResource,
    permanentDeleteResource,
    updateProfile,
    // 轻量审计日志
    loadAdminLogs,
    recordAdminLog
  }
})
