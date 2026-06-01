<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadRecycleResources({ keyword: keyword.value.trim() })
  } finally {
    loading.value = false
  }
}

async function restore(id: number, title: string) {
  try {
    await ElMessageBox.confirm(`确认恢复「${title}」到资源列表？`, '恢复资源', { type: 'warning' })
    await store.restoreResource(id)
    await store.recordAdminLog('RESOURCE_RESTORE', id, title)
    ElMessage.success('已恢复')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '恢复失败')
    }
  }
}

async function permanentDelete(id: number, title: string) {
  try {
    await ElMessageBox.confirm(`永久删除「${title}」？此操作不可恢复！`, '永久删除', { type: 'error' })
    await store.permanentDeleteResource(id)
    await store.recordAdminLog('RESOURCE_PERMANENT_DELETE', id, title)
    ElMessage.success('已永久删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>回收站</h1>
        <p class="sub">管理员可查看已删除的资源。</p>
      </div>
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索回收站" style="width: 260px" @keyup.enter="load" />
        <el-button @click="load">查询</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="store.recycleResources" class="panel">
      <el-table-column label="资源" min-width="260">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub">作者：{{ row.author || ('用户 ' + (row.userId || '-')) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="cat" label="分类" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column prop="downloads" label="下载" width="80" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="restore(row.id, row.title)">恢复</el-button>
          <el-button size="small" type="danger" plain @click="permanentDelete(row.id, row.title)">永久删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && store.recycleResources.length === 0" description="回收站为空" />
  </AdminLayout>
</template>
