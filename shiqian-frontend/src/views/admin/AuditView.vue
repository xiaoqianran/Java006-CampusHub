<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  store.loadResources().catch(() => undefined)
})

async function approve(id: number) {
  try {
    await store.approveResource(id)
    ElMessage.success('已通过，资源将进入资源广场')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}

async function reject(id: number) {
  try {
    await store.rejectResource(id)
    ElMessage.warning('已驳回，发布者可在我的发布中查看')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>资源审核</h1>
        <p class="sub">审核通过进入资源广场；驳回后回到发布者的“我的发布”。</p>
      </div>
    </div>
    <el-table :data="store.pendingResources" class="panel" empty-text="暂无待审核资源">
      <el-table-column label="资源" min-width="300"><template #default="{ row }"><b>{{ row.title }}</b><div class="sub">{{ row.desc }}</div></template></el-table-column>
      <el-table-column prop="author" label="发布者" width="120" />
      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row.id)">通过</el-button>
          <el-button size="small" type="danger" @click="reject(row.id)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </AdminLayout>
</template>
