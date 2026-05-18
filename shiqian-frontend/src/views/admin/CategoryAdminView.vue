<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  store.loadCategories().catch(() => undefined)
})

async function addCategory() {
  const name = await ElMessageBox.prompt('请输入分类名称', '新增分类')
    .then(({ value }) => value)
    .catch(() => '')
  if (!name) return
  try {
    await store.createCategory(name)
    ElMessage.success('分类已新增')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新增失败')
  }
}

async function editCategory(id: number, oldName: string) {
  const name = await ElMessageBox.prompt('请输入分类名称', '编辑分类', { inputValue: oldName })
    .then(({ value }) => value)
    .catch(() => '')
  if (!name) return
  try {
    await store.updateCategory(id, name)
    ElMessage.success('分类已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败')
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>分类管理</h1>
        <p class="sub">分类与用户端分类浏览共用同一套数据。</p>
      </div>
      <el-button type="primary" @click="addCategory">新增分类</el-button>
    </div>
    <div class="category-grid">
      <el-card v-for="category in store.flatCategories" :key="category.id" class="category-card" shadow="never">
        <span class="category-icon">📁</span>
        <span style="flex: 1"><b>{{ category.name }}</b><br><span class="sub">{{ store.resources.filter(item => item.cat === category.name).length }} 个资源</span></span>
        <el-button size="small" @click="editCategory(category.id, category.name)">编辑</el-button>
      </el-card>
    </div>
  </AdminLayout>
</template>
