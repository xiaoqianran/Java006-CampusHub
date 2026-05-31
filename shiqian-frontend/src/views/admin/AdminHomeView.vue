<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

const greeting = computed(() => {
  const u = store.currentUser
  return u?.nickname || u?.username || '管理员'
})

const recentPending = computed(() => store.pendingResources.slice(0, 5))

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

async function approve(id: number) {
  try {
    await store.approveResource(id)
    ElMessage.success('已通过')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}

async function reject(id: number) {
  try {
    await store.rejectResource(id)
    ElMessage.warning('已驳回')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>欢迎回来，{{ greeting }}</h1>
        <p class="sub">后台统一管理资源审核、分类、用户与数据统计。</p>
      </div>
    </div>
    <section class="stat-grid">
      <div class="stat-card"><b>{{ store.pendingResources.length }}</b><span class="sub">待审核</span></div>
      <div class="stat-card"><b>{{ store.publishedResources.length }}</b><span class="sub">已发布</span></div>
      <div class="stat-card"><b>{{ store.rejectedResources.length }}</b><span class="sub">已驳回</span></div>
      <div class="stat-card"><b>{{ store.reviewableResources.length }}</b><span class="sub">待复核</span></div>
    </section>
    <section class="section">
      <div class="page-title"><h1>最近待办</h1></div>
      <el-table :data="recentPending" class="panel">
        <el-table-column label="资源" min-width="300"><template #default="{ row }"><b>{{ row.title }}</b><div class="sub">{{ row.desc }}</div></template></el-table-column>
        <el-table-column prop="author" label="发布者" width="120" /><!-- 真实作者来自后端 authorNickname -->
        <el-table-column prop="cat" label="分类" width="130" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="approve(row.id)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row.id)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="store.pendingResources.length === 0" class="sub" style="margin-top:8px;">暂无待审核资源 (status=0)。</div>
    </section>

    <section class="section">
      <div class="page-title"><h1>快速入口</h1></div>
      <div style="display:flex;gap:12px;flex-wrap:wrap;">
        <el-button @click="$router.push('/admin/audit')">资源审核</el-button>
        <el-button @click="$router.push('/admin/resources')">资源管理</el-button>
        <el-button @click="$router.push('/admin/categories')">分类管理</el-button>
        <el-button @click="$router.push('/admin/users')">用户管理</el-button>
        <el-button @click="$router.push('/admin/recycle-bin')">回收站</el-button>
        <el-button @click="$router.push('/admin/logs')">操作日志</el-button>
      </div>
    </section>
  </AdminLayout>
</template>
