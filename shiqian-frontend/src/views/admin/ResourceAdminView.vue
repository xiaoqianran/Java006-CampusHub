<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  store.loadResources().catch(() => undefined)
})

async function takeDownResource(id: number, title: string) {
  try {
    await ElMessageBox.confirm(`确认下架「${title}」？下架后其他用户将无法看到，发布者本人仍可在我的发布中查看。`, '下架资源', {
      type: 'warning',
      confirmButtonText: '下架',
      cancelButtonText: '取消'
    })
    await store.takeDownResource(id)
    ElMessage.success('已下架')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '下架失败')
    }
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>资源管理</h1>
        <p class="sub">管理全部资源，不只是待审核资源。</p>
      </div>
    </div>
    <el-table :data="store.resources" class="panel">
      <el-table-column label="资源" min-width="280"><template #default="{ row }"><b>{{ row.title }}</b></template></el-table-column>
      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column prop="downloads" label="下载" width="100" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/detail/${row.id}`)">预览</el-button>
          <el-button size="small" type="danger" plain :disabled="row.status === '已驳回'" @click="takeDownResource(row.id, row.title)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>
  </AdminLayout>
</template>
