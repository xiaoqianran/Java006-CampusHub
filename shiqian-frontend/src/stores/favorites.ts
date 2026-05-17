import { defineStore } from 'pinia'
import { resourceApi } from '../api/resource'
import type { ResourceItem } from '../types/resource'
import { useAuthStore } from './auth'
import { ElMessage } from 'element-plus'

interface FavoritesState {
  ids: Set<number>
  details: Map<number, ResourceItem>
  loadingIds: Set<number>
}

export const useFavoritesStore = defineStore('favorites', {
  state: (): FavoritesState => ({
    ids: new Set(),
    details: new Map(),
    loadingIds: new Set()
  }),
  getters: {
    isFavorited: (state) => (id: number) => state.ids.has(id),
    favoriteList: (state) => Array.from(state.details.values()),
    count: (state) => state.ids.size
  },
  actions: {
    // 乐观添加
    async toggleFavorite(resourceId: number) {
      const auth = useAuthStore()
      if (!auth.isAuthenticated) {
        ElMessage.warning('请先登录后再收藏')
        return false
      }

      const isFav = this.ids.has(resourceId)
      this.loadingIds.add(resourceId)

      // 乐观更新
      if (isFav) {
        this.ids.delete(resourceId)
        this.details.delete(resourceId)
      } else {
        this.ids.add(resourceId)
      }

      try {
        if (isFav) {
          await resourceApi.removeFavorite(resourceId)
          ElMessage.success('已取消收藏')
        } else {
          await resourceApi.addFavorite(resourceId)
          ElMessage.success('收藏成功！可在个人中心查看')
          // 如果有详情，尝试缓存（详情页会传进来）
        }
        return true
      } catch (e: any) {
        // 回滚
        if (isFav) {
          this.ids.add(resourceId)
        } else {
          this.ids.delete(resourceId)
          this.details.delete(resourceId)
        }
        ElMessage.error(e.message || '操作失败')
        return false
      } finally {
        this.loadingIds.delete(resourceId)
      }
    },

    // 详情页或列表传入真实详情用于缓存
    cacheDetail(res: ResourceItem) {
      if (res?.id) {
        this.details.set(res.id, res)
        this.ids.add(res.id) // 确保 id 在集合中
      }
    },

    async hydrateMyFavorites() {
      if (this.ids.size === 0) return
      const ids = Array.from(this.ids).slice(0, 30) // 保护后端
      const promises = ids.map(id =>
        resourceApi.getResource(id).catch(() => null)
      )
      const results = await Promise.all(promises)
      this.details.clear()
      for (const r of results) {
        if (r?.id) this.details.set(r.id, r)
      }
    },

    // 从服务端同步单个状态（详情页进入时调用）
    async syncStatus(resourceId: number) {
      const auth = useAuthStore()
      if (!auth.isAuthenticated) return
      try {
        const fav = await resourceApi.isFavorited(resourceId)
        if (fav) {
          this.ids.add(resourceId)
        } else {
          this.ids.delete(resourceId)
          this.details.delete(resourceId)
        }
      } catch {}
    },

    remove(id: number) {
      this.ids.delete(id)
      this.details.delete(id)
    }
  }
})