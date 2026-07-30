<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import ResourceCard from '@/components/ResourceCard.vue'
import { CONTENT_SCENES, useAppStore, type ContentScene } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const homeKeyword = ref('')
const resourceMode = ref<'newest' | 'hottest'>('newest')
const scenePaths: Record<ContentScene, string> = {
  BLOG: '/blog',
  GALLERY: '/images',
  SHARE: '/share'
}

const featuredResources = computed(() => {
  const resources = resourceMode.value === 'hottest'
    ? [...store.publishedResources].sort((a, b) =>
        ((b.downloads || 0) + (b.views || 0)) - ((a.downloads || 0) + (a.views || 0)) || b.id - a.id
      )
    : [...store.publishedResources].sort((a, b) => b.id - a.id)
  return resources.slice(0, 6)
})

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

function search() {
  const keyword = homeKeyword.value.trim()
  router.push({
    path: '/explore',
    query: keyword ? { keyword } : {}
  })
}

function openScene(scene: ContentScene) {
  router.push(scenePaths[scene])
}

function viewAllResources() {
  router.push({
    path: '/explore',
    query: resourceMode.value === 'hottest' ? { sort: 'hottest' } : {}
  })
}
</script>

<template>
  <section class="hero">
    <h1>分享校园里的每一种内容</h1>
    <p>写博客、晒图片、分享课件与源码；内容不再被分类和格式限制。</p>
    <el-input v-model="homeKeyword" class="hero-search" size="large" placeholder="搜索文章、图片、资料或自由标签" :prefix-icon="Search" @keyup.enter="search">
      <template #append>
        <el-button type="primary" @click="search">搜索内容</el-button>
      </template>
    </el-input>
  </section>

  <section class="section">
    <div class="page-title">
      <div>
        <h1>选择你想看的频道</h1>
        <p class="sub">频道只区分展示方式，每个频道都能包含文字、图片和附件。</p>
      </div>
      <el-button text type="primary" :icon="ArrowRight" @click="router.push('/explore')">发现全部内容</el-button>
    </div>
    <div v-loading="store.loading" class="channel-shortcuts">
      <button
        v-for="channel in CONTENT_SCENES"
        :key="channel.value"
        type="button"
        class="channel-shortcut"
        @click="openScene(channel.value)"
      >
        <span>
          <b>{{ channel.label }}</b>
          <small>{{ channel.description }}</small>
        </span>
        <strong>{{ store.publishedResources.filter(item => item.scene === channel.value).length }}</strong>
      </button>
    </div>
  </section>

  <section class="section">
    <div class="page-title resource-section-title">
      <div>
        <h1>内容推荐</h1>
        <p class="sub">博客、图片和资料混合呈现。</p>
      </div>
      <el-radio-group v-model="resourceMode">
        <el-radio-button value="newest">最新发布</el-radio-button>
        <el-radio-button value="hottest">热门内容</el-radio-button>
      </el-radio-group>
    </div>
    <div v-loading="store.loading" class="resource-grid" style="min-height: 120px;">
      <ResourceCard v-for="item in featuredResources" :key="`${resourceMode}-${item.id}`" :item="item" />
    </div>
    <div class="section-more">
      <el-button type="primary" plain :icon="ArrowRight" @click="viewAllResources">查看全部内容</el-button>
    </div>
  </section>
</template>

<style scoped>
.channel-shortcuts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 70px;
}

.channel-shortcut {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 92px;
  padding: 18px;
  color: var(--text);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  font: inherit;
  cursor: pointer;
  transition: border-color .18s, background-color .18s, transform .18s;
}

.channel-shortcut:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
  transform: translateY(-1px);
}

.channel-shortcut span {
  display: grid;
  gap: 7px;
  text-align: left;
}

.channel-shortcut small {
  color: var(--muted);
  line-height: 1.4;
}

.channel-shortcut strong {
  color: var(--primary);
  font-size: 22px;
}

.section-more {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 980px) {
  .channel-shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .channel-shortcuts {
    grid-template-columns: 1fr;
  }

  .resource-section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
