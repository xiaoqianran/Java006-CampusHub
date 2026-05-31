<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

const searchText = ref('')
const statusFilter = ref<'全部' | '已发布' | '已驳回'>('全部')

const filteredAdminResources = computed(() => {
  let list = store.resources
  const text = searchText.value.trim().toLowerCase()
  if (text) {
    list = list.filter(r =>
      r.title.toLowerCase().includes(text) ||
      (r.desc || '').toLowerCase().includes(text) ||
      (r.cat || '').toLowerCase().includes(text) ||
      (r.author || '').toLowerCase().includes(text)
    )
  }
  if (statusFilter.value !== '全部') {
    list = list.filter(r => r.status === statusFilter.value)
  }
  return list
})

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
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;flex-wrap:wrap;">
      <el-input v-model="searchText" placeholder="搜索标题/描述/分类/作者..." clearable style="width:280px;" />
      <el-radio-group v-model="statusFilter">
        <el-radio-button label="全部">全部</el-radio-button>
        <el-radio-button label="已发布">已发布</el-radio-button>
        <el-radio-button label="已驳回">已驳回</el-radio-button>
      </el-radio-group>
      <span class="sub">共 {{ filteredAdminResources.length }} 条（使用 store.resources 丰富数据）</span>
    </div>
    <el-table :data="filteredAdminResources" class="panel">
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
