<script setup lang="ts">
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>欢迎回来，李老师</h1>
        <p class="sub">后台统一管理资源审核、分类、用户与数据统计。</p>
      </div>
    </div>
    <section class="stat-grid">
      <div class="stat-card"><b>{{ store.pendingResources.length }}</b><span class="sub">待审核</span></div>
      <div class="stat-card"><b>{{ store.publishedResources.length }}</b><span class="sub">已发布</span></div>
      <div class="stat-card"><b>{{ store.categories.length }}</b><span class="sub">分类</span></div>
      <div class="stat-card"><b>{{ store.users.length }}</b><span class="sub">用户</span></div>
    </section>
    <section class="section">
      <div class="page-title"><h1>最近待办</h1></div>
      <el-table :data="store.pendingResources" class="panel">
        <el-table-column label="资源" min-width="300"><template #default="{ row }"><b>{{ row.title }}</b><div class="sub">{{ row.desc }}</div></template></el-table-column>
        <el-table-column prop="author" label="发布者" width="120" />
        <el-table-column prop="cat" label="分类" width="130" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="store.approveResource(row.id)">通过</el-button>
            <el-button size="small" type="danger" @click="store.rejectResource(row.id)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </AdminLayout>
</template>
