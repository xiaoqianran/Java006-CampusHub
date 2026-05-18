<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  if (!store.logged) return
  store.loadMyResources().catch(error => {
    ElMessage.error(error instanceof Error ? error.message : '我的发布加载失败')
  })
})

async function remove(id: number) {
  await ElMessageBox.confirm('确定删除这条发布记录吗？', '删除确认', { type: 'warning' })
  try {
    await store.removeMyResource(id)
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>我的发布</h1>
        <p class="sub">显示待审核、已发布、已驳回三种状态，并提供查看/删除入口。</p>
      </div>
    </div>
    <el-table :data="store.myResources" class="panel" style="width: 100%">
      <el-table-column label="资源" min-width="280">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub">{{ row.desc }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column label="数据" width="170"><template #default="{ row }">{{ row.views }} 浏览 / {{ row.downloads }} 下载</template></el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/detail/${row.id}`)">查看</el-button>
          <el-button size="small" type="danger" plain @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
