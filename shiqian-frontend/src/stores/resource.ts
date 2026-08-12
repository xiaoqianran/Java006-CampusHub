import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  jsonBody,
  request,
  uploadRequest,
  type PageResult
} from '@/api/client'
import { useAuthStore } from './auth'
import { useCatalogStore } from './catalog'
import {
  mapStatus,
  type ContentScene,
  type ContentSceneFilter,
  type HomeLoadOptions,
  type LoadOptions,
  type ResourceApiItem,
  type ResourceDetailLoadOptions,
  type ResourceItem,
  type ResourceSubmitPayload,
  type ResourceUpdatePayload,
  type ResourceVersionItem,
  type UploadedFileItem
} from './types'

const DATA_CACHE_TTL_MS = 30_000

/**
 * 资源域 store：列表/详情/搜索/收藏/我的/回收站与相关 CRUD。
 * 全局广场筛选（keyword / activeScene / sortMode / activeCategory）也放这里，
 * 因为 filteredResources / searchResources / loadResources 与之强耦合。
 */
export const useResourceStore = defineStore('resource', () => {
  const activeCategory = ref<string>('全部分类')
  const activeScene = ref<ContentSceneFilter>('ALL')
  const keyword = ref('')
  const sortMode = ref<'newest' | 'hottest'>('newest')
  const loading = ref(false)

  const resources = ref<ResourceItem[]>([])
  const recycleResources = ref<ResourceItem[]>([])
  const favoriteIds = ref<number[]>([])
  const myResourceIds = ref<number[]>([])
  const searchResultIds = ref<number[] | null>(null)
  const searchResultTotal = ref(0)
  const searchLoading = ref(false)

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
  let homeDataInFlight: Promise<void> | null = null
  let coreDataLoaded = false
  let searchAbortController: AbortController | null = null
  let searchSequence = 0

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
      const matchScene = activeScene.value === 'ALL' || item.scene === activeScene.value
      return matchScene && (!text || searchResultIds.value ||
        `${item.title}${item.tags || ''}${item.type}${item.desc}`.includes(text))
    })
    // 搜索时保留后端返回顺序；频道过滤已经在查询参数中完成。
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

  function mapResource(item: ResourceApiItem): ResourceItem {
    const catalog = useCatalogStore()
    const categoryIds = item.categoryIds?.length
      ? item.categoryIds
      : item.categoryId ? [item.categoryId] : []
    const categoryNames = item.categoryNames?.length
      ? item.categoryNames
      : categoryIds.map(id => catalog.categoryName(id)).filter(name => name !== '未分类')
    const tagNames = item.tagNames?.length
      ? item.tagNames
      : (item.tags || '').split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
    return {
      id: item.id,
      title: item.title,
      cat: categoryNames[0] || catalog.categoryName(item.categoryId),
      categoryId: categoryIds[0],
      categoryIds,
      categoryNames,
      scene: item.contentScene || 'SHARE',
      tags: tagNames.join(','),
      tagIds: item.tagIds || [],
      tagNames,
      version: item.version || 1,
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
      publishedTime: item.publishedTime,
      searchHighlights: item.searchHighlights
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

  function resourceRequestKey(params: {
    page?: number
    size?: number
    categoryId?: number
    keyword?: string
    sort?: string
    scene?: ContentScene
    tagId?: number
    tag?: string
  }) {
    const auth = useAuthStore()
    return JSON.stringify({
      scope: `${auth.logged}:${auth.role}`,
      page: params.page ?? 1,
      size: params.size ?? 100,
      categoryId: params.categoryId ?? null,
      keyword: params.keyword?.trim() || '',
      sort: params.sort ?? sortMode.value,
      scene: params.scene ?? null,
      tagId: params.tagId ?? null,
      tag: params.tag?.trim() || ''
    })
  }

  function invalidateResourceCache(id?: number) {
    resourcesLoadedAt.clear()
    if (id !== undefined) {
      detailLoadedAt.delete(id)
      favoriteStateLoadedAt.delete(id)
    }
  }

  /** 登录/登出后清空与会话相关的列表缓存与收藏/我的 ID。 */
  function clearSessionScopedState() {
    favoriteIds.value = []
    myResourceIds.value = []
    invalidateAuthScopedCaches()
    favoriteStateLoadedAt.clear()
  }

  function invalidateAuthScopedCaches() {
    resourcesLoadedAt.clear()
    favoritesLoadedAt.clear()
    myResourcesLoadedAt.clear()
  }

  async function loadResources(
    params: {
      page?: number
      size?: number
      categoryId?: number
      keyword?: string
      sort?: string
      scene?: ContentScene
      tagId?: number
      tag?: string
    } = {},
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
        sort: params.sort ?? sortMode.value,
        scene: params.scene,
        tagId: params.tagId,
        tag: params.tag
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
    const auth = useAuthStore()
    if (options.includePersonal && auth.logged) {
      void Promise.allSettled([
        loadFavorites(),
        loadMyResources(),
        auth.loadCurrentUser()
      ])
    } else if (auth.logged && !auth.currentUser) {
      void auth.loadCurrentUser().catch(() => undefined)
    }

    if (!options.force && homeDataInFlight) return homeDataInFlight
    const hasUsableData = coreDataLoaded
    const task = (async () => {
      if (!hasUsableData) loading.value = true
      await loadResources({}, { force: options.force })
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

  async function searchResources(params: {
    sort?: string
    scene?: ContentSceneFilter
    page?: number
    size?: number
    categoryId?: number
    tagId?: number
    tag?: string
  } = {}) {
    searchAbortController?.abort()
    const sequence = ++searchSequence
    const text = keyword.value.trim()
    const sort = params.sort ?? sortMode.value
    const scene = params.scene ?? activeScene.value
    const requestedScene = scene === 'ALL' ? undefined : scene
    const page = params.page ?? 1
    const size = params.size ?? 24

    const controller = new AbortController()
    searchAbortController = controller
    searchLoading.value = true
    try {
      const data = await request<PageResult<ResourceApiItem>>(
        text ? '/api/resource/search' : '/api/resource',
        {
        query: {
          keyword: text || undefined,
          page,
          size,
          sort,
          scene: requestedScene,
          categoryId: params.categoryId,
          tagId: params.tagId,
          tag: params.tag
        },
        signal: controller.signal
      })
      if (sequence !== searchSequence) return
      searchResultIds.value = data.records.map(item => item.id)
      searchResultTotal.value = data.total
      mergeResources(data.records)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw error
    } finally {
      if (searchAbortController === controller) {
        searchAbortController = null
        searchLoading.value = false
      }
    }
  }

  function cancelResourceSearch() {
    searchSequence += 1
    searchAbortController?.abort()
    searchAbortController = null
    searchLoading.value = false
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
    if (useAuthStore().logged && options.includeFavorite !== false) {
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

  function setCategory(category: string) {
    activeCategory.value = category
  }

  async function resetFilters() {
    activeCategory.value = '全部分类'
    activeScene.value = 'ALL'
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
    const catalog = useCatalogStore()
    const categoryNames = payload.categories?.length
      ? payload.categories
      : payload.cat ? [payload.cat] : []
    const categoryIds = categoryNames
      .map(name => catalog.categoryId(name))
      .filter((id): id is number => id !== undefined)
    const tagNames = payload.tagNames?.length
      ? payload.tagNames.map(tag => tag.trim()).filter(Boolean)
      : (payload.tags || '').split(/[,，]/).map(tag => tag.trim()).filter(Boolean)

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
    if (!contentMarkdown && !attachments.length) {
      throw new Error('请至少填写正文、上传图片或添加一个附件')
    }
    const contentType = attachments.length && contentMarkdown
      ? 'MIXED'
      : attachments.length ? 'FILE' : 'ARTICLE'

    // 第二阶段：一个资源 + attachments 数组
    await request<void>('/api/resource', {
      method: 'POST',
      body: jsonBody({
        title: payload.title,
        categoryId: categoryIds[0],
        categoryIds,
        summary: payload.summary,
        contentMarkdown,
        contentType,
        contentScene: payload.contentScene,
        tags: tagNames.join(',') || undefined,
        tagNames,
        attachments
      })
    })

    invalidateResourceCache()
    myResourcesLoadedAt.clear()
    await loadMyResources({}, { force: true })
  }

  async function updateResource(id: number, payload: ResourceUpdatePayload) {
    const catalog = useCatalogStore()
    const categoryNames = payload.categories?.length
      ? payload.categories
      : payload.cat ? [payload.cat] : []
    const categoryIds = categoryNames
      .map(name => catalog.categoryId(name))
      .filter((value): value is number => value !== undefined)
    const tagNames = payload.tagNames?.length
      ? payload.tagNames.map(tag => tag.trim()).filter(Boolean)
      : (payload.tags || '').split(/[,，]/).map(tag => tag.trim()).filter(Boolean)

    const existing = getResource(id)
    const hasFiles = payload.attachments !== undefined
      ? payload.attachments.length > 0
      : Boolean(payload.file || existing?.attachments?.length || existing?.fileUrl)
    const hasText = Boolean(payload.contentMarkdown.trim())

    const body: any = {
      title: payload.title,
      categoryId: categoryIds[0],
      categoryIds,
      summary: payload.summary,
      description: payload.summary,
      contentMarkdown: payload.contentMarkdown,
      contentType: hasFiles && hasText ? 'MIXED' : hasFiles ? 'FILE' : 'ARTICLE',
      contentScene: payload.contentScene,
      tags: tagNames.join(','),
      tagNames,
      changeDescription: payload.changeDescription?.trim() || undefined
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
      // 作者修改已发布内容后后端会重新进审
      if (item.status === '已发布') {
        item.status = '待审核'
        item.reviewReason = undefined
        item.offlineReason = undefined
      }
      item.title = payload.title
      item.cat = categoryNames[0] || '未分类'
      item.categoryId = categoryIds[0]
      item.categoryIds = categoryIds
      item.categoryNames = categoryNames
      item.scene = payload.contentScene
      item.tags = tagNames.join(',')
      item.tagNames = tagNames
      item.version += 1
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

  async function loadResourceVersions(id: number) {
    return request<ResourceVersionItem[]>(`/api/resource/${id}/versions`)
  }

  async function rollbackResourceVersion(
    id: number,
    version: number,
    changeDescription?: string
  ) {
    const nextVersion = await request<number>(
      `/api/resource/${id}/versions/${version}/rollback`,
      {
        method: 'POST',
        body: jsonBody({ changeDescription: changeDescription?.trim() || undefined })
      }
    )
    invalidateResourceCache(id)
    myResourcesLoadedAt.clear()
    await loadResourceDetail(id, { force: true })
    return nextVersion
  }

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

  return {
    activeCategory,
    activeScene,
    keyword,
    sortMode,
    loading,
    searchLoading,
    searchResultTotal,
    resources,
    recycleResources,
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
    setCategory,
    resetFilters,
    loadHomeData,
    loadResources,
    loadRecycleResources,
    searchResources,
    cancelResourceSearch,
    loadResourceDetail,
    loadFavorites,
    loadMyResources,
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
    loadResourceVersions,
    rollbackResourceVersion,
    restoreResource,
    permanentDeleteResource,
    invalidateResourceCache,
    clearSessionScopedState,
    invalidateAuthScopedCaches
  }
})
