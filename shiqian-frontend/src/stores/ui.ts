import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 纯 UI 状态：主题等。
 * 广场筛选（keyword / activeScene / sortMode / activeCategory）放在 resource store，
 * 因为它们与 filteredResources / searchResources 强耦合。
 */
export const useUiStore = defineStore('ui', () => {
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

  return {
    theme,
    isDark,
    initTheme,
    setTheme,
    toggleTheme
  }
})
