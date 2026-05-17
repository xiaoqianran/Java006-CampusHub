import { defineStore } from 'pinia'
import { resourceApi } from '../api/resource'
import type { Category } from '../types/resource'

interface CategoryState {
  tree: Category[]
  flat: Map<number, Category>
  loading: boolean
}

export const useCategoryStore = defineStore('category', {
  state: (): CategoryState => ({
    tree: [],
    flat: new Map(),
    loading: false
  }),
  getters: {
    categoryTree: (state) => state.tree,
    categoryMap: (state) => state.flat,
    getCategoryName: (state) => (id?: number) => {
      if (!id) return '未分类'
      return state.flat.get(id)?.name || `分类#${id}`
    }
  },
  actions: {
    async loadTree(force = false) {
      if (this.tree.length > 0 && !force) return
      this.loading = true
      try {
        const tree = await resourceApi.listCategoryTree()
        this.tree = tree || []
        this.flat.clear()
        this._flatten(this.tree)
      } catch (e) {
        this.tree = []
      } finally {
        this.loading = false
      }
    },
    _flatten(nodes: Category[]) {
      for (const node of nodes) {
        this.flat.set(node.id, node)
        if (node.children?.length) {
          this._flatten(node.children)
        }
      }
    },
    // 用于上传表单的级联选项
    getCascaderOptions() {
      return this._toCascader(this.tree)
    },
    _toCascader(nodes: Category[]): any[] {
      return nodes.map(n => ({
        value: n.id,
        label: n.name,
        children: n.children?.length ? this._toCascader(n.children) : undefined
      }))
    }
  }
})