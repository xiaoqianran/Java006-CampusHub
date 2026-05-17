<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, StarFilled, Download } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const resource = computed(() => store.getResource(Number(route.params.id)) || store.resources[0])
const related = computed(() => store.resources.filter(item => item.cat === resource.value.cat && item.id !== resource.value.id).slice(0, 3))
</script>

<template>
  <section class="detail-layout">
    <el-card class="detail-card" shadow="never">
      <el-tag>{{ resource.cat }}</el-tag>
      <h1 class="detail-title">{{ resource.title }}</h1>
      <p class="sub">{{ resource.desc }}</p>
      <div class="resource-meta">
        <span>作者：{{ resource.author }}</span>
        <span>浏览 {{ resource.views }}</span>
        <span>下载 {{ resource.downloads }}</span>
        <span>收藏 {{ resource.favs }}</span>
      </div>
      <div style="margin: 24px 0; display: flex; gap: 12px">
        <el-button type="primary" :icon="Download">下载资源</el-button>
        <el-button :icon="store.isFavorite(resource.id) ? StarFilled : Star" @click="store.toggleFavorite(resource.id)">
          {{ store.isFavorite(resource.id) ? '取消收藏' : '加入收藏' }}
        </el-button>
      </div>
      <h2>资源说明</h2>
      <p>这里统一使用详情页模板，承接来自首页、广场、分类、收藏、我的发布的跳转。页面右侧展示作者、审核状态和相关推荐。</p>
      <h2>评论区</h2>
      <div class="comment"><b>张同学</b><p class="sub">资料很完整，实验步骤可以直接对照学习。</p></div>
      <div class="comment"><b>管理员</b><p class="sub">已通过基础内容检查，下载前请注意课程版本差异。</p></div>
    </el-card>
    <aside>
      <el-card shadow="never">
        <h3>资源状态</h3>
        <StatusTag :status="resource.status" />
        <h3>相关推荐</h3>
        <div v-if="related.length">
          <p v-for="item in related" :key="item.id">
            <a @click="router.push(`/detail/${item.id}`)"><b>{{ item.title }}</b></a><br>
            <span class="sub">{{ item.type }}</span>
          </p>
        </div>
        <p v-else class="sub">暂无相关资源</p>
      </el-card>
    </aside>
  </section>
</template>
