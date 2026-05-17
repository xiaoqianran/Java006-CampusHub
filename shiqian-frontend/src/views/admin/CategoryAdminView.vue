<script setup lang="ts">
import { ElMessage } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>分类管理</h1>
        <p class="sub">分类与用户端分类浏览共用同一套数据。</p>
      </div>
      <el-button type="primary" @click="ElMessage.info('新增分类：原型中仅演示交互')">新增分类</el-button>
    </div>
    <div class="category-grid">
      <el-card v-for="category in store.categories" :key="category" class="category-card" shadow="never">
        <span class="category-icon">📁</span>
        <span style="flex: 1"><b>{{ category }}</b><br><span class="sub">{{ store.resources.filter(item => item.cat === category).length }} 个资源</span></span>
        <el-button size="small">编辑</el-button>
      </el-card>
    </div>
  </AdminLayout>
</template>
