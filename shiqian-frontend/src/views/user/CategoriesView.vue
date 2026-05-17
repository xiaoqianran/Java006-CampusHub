<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const icons = ['💻', '∑', 'A', '🎓', '📝', '🧪', '🏆', '🏫']

function openCategory(category: string) {
  store.setCategory(category)
  router.push('/plaza')
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
    <div class="category-grid">
      <el-card v-for="(category, index) in store.categories" :key="category" class="category-card" shadow="never" @click="openCategory(category)">
        <span class="category-icon">{{ icons[index] }}</span>
        <span><b>{{ category }}</b><br><span class="sub">{{ store.resources.filter(item => item.cat === category).length }} 个资源</span></span>
      </el-card>
    </div>
  </section>
</template>
