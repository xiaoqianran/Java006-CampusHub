import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { jsonBody, request } from '@/api/client'
import { flattenCategories, type CategoryApiItem, type LoadOptions, type TagApiItem } from './types'

const DATA_CACHE_TTL_MS = 30_000
const fallbackCategories = ['计算机科学', '高等数学', '大学英语', '考研资料', '课程笔记', '实验报告', '竞赛资料', '校园生活']

// 动态取 resource store，避免 catalog ↔ resource 静态循环依赖。
async function invalidateResourceCaches() {
  const { useResourceStore } = await import('./resource')
  useResourceStore().invalidateResourceCache()
}

export const useCatalogStore = defineStore('catalog', () => {
  const categoryTree = ref<CategoryApiItem[]>([])
  const tags = ref<TagApiItem[]>([])

  let categoriesLoadedAt = 0
  let categoriesInFlight: Promise<void> | null = null

  const flatCategories = computed(() => flattenCategories(categoryTree.value))
  const categories = computed(() => flatCategories.value.length ? flatCategories.value.map(item => item.name) : fallbackCategories)

  function isFresh(loadedAt?: number) {
    return Boolean(loadedAt && Date.now() - loadedAt < DATA_CACHE_TTL_MS)
  }

  function categoryName(categoryId?: number) {
    return flatCategories.value.find(item => item.id === categoryId)?.name || '未分类'
  }

  function categoryId(category: string) {
    return flatCategories.value.find(item => item.name === category)?.id
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

  async function loadTags(keyword?: string) {
    tags.value = await request<TagApiItem[]>('/api/tag', {
      query: { keyword: keyword?.trim() || undefined }
    })
    return tags.value
  }

  async function createTag(name: string) {
    const created = await request<TagApiItem>('/api/tag', {
      method: 'POST',
      body: jsonBody({ name: name.trim() })
    })
    await loadTags()
    return created
  }

  async function updateTag(id: number, name: string) {
    const updated = await request<TagApiItem>(`/api/tag/${id}`, {
      method: 'PUT',
      body: jsonBody({ name: name.trim() })
    })
    await loadTags()
    await invalidateResourceCaches()
    return updated
  }

  async function deleteTag(id: number) {
    await request<void>(`/api/tag/${id}`, { method: 'DELETE' })
    await loadTags()
    await invalidateResourceCaches()
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

  return {
    categoryTree,
    tags,
    flatCategories,
    categories,
    categoryName,
    categoryId,
    loadCategories,
    loadTags,
    createTag,
    updateTag,
    deleteTag,
    createCategory,
    updateCategory,
    deleteCategory
  }
})
