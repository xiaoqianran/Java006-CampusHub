<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

function openCategory(category: string) {
  store.setCategory(category)
  router.push('/plaza')
}

// 分类浏览页专用 icon 获取函数，保证永远有图标
function getCategoryIcon(category: string, index: number): string {
  const preset: Record<string, string> = {
    '计算机科学': '💻',
    '高等数学': '∑',
    '大学英语': 'A',
    '考研资料': '📝',
    '课程笔记': '🧪',
    '实验报告': '🏆',
    '竞赛资料': '🏫',
    '校园生活': '📌'
  }
  if (preset[category]) return preset[category]
  // 动态分类使用稳定 fallback
  const fallbacks = ['📁', '📚', '🧾', '🧠', '📝', '⭐', '📌', '🎓']
  return fallbacks[index % fallbacks.length]
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>分类浏览</h1>
        <p class="sub">分类不是孤立页面，而是资源广场的筛选入口。</p>
      </div>
    </div>
    <div class="category-grid category-browse-grid">
      <el-card
        v-for="(category, index) in store.categories"
        :key="category"
        class="category-card category-browse-card"
        shadow="never"
        @click="openCategory(category)"
      >
        <span class="category-icon category-browse-icon">
          {{ getCategoryIcon(category, index) }}
        </span>
        <b class="category-name">{{ category }}</b>
        <span class="sub category-count">
          {{ store.resources.filter(item => item.cat === category).length }} 个资源
        </span>
      </el-card>
    </div>
  </section>
</template>
