<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { resourceApi } from '../api/resource';
import type { Category, ResourceItem } from '../types/resource';

const loading = ref(false);
const errorMessage = ref('');
const resources = ref<ResourceItem[]>([]);
const categories = ref<Category[]>([]);
const total = ref(0);
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  categoryId: 0
});

const categoryOptions = computed(() => flattenCategories(categories.value));

function flattenCategories(items: Category[], depth = 0): Array<Category & { label: string }> {
  return items.flatMap((item) => [
    { ...item, label: `${'　'.repeat(depth)}${item.name}` },
    ...flattenCategories(item.children ?? [], depth + 1)
  ]);
}

async function loadResources() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await resourceApi.pageResources({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined
    });
    resources.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资源加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadCategories() {
  try {
    categories.value = await resourceApi.listCategoryTree();
  } catch (error) {
    categories.value = [];
  }
}

async function handleSearch() {
  query.page = 1;
  await loadResources();
}

async function handleCategoryChange() {
  query.page = 1;
  await loadResources();
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadResources()]);
});
</script>

<template>
  <section class="resource-page">
    <form class="resource-toolbar" @submit.prevent="handleSearch">
      <input
        v-model="query.keyword"
        name="keyword"
        placeholder="搜索资源标题或描述"
        aria-label="搜索资源标题或描述"
      />
      <select
        v-model.number="query.categoryId"
        name="categoryId"
        aria-label="分类筛选"
        @change="handleCategoryChange"
      >
        <option :value="0">全部分类</option>
        <option
          v-for="category in categoryOptions"
          :key="category.id"
          :value="category.id"
        >
          {{ category.label }}
        </option>
      </select>
      <button class="primary-button" type="submit">搜索</button>
    </form>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="loading" class="muted-state">资源加载中</p>
    <p v-else-if="resources.length === 0" class="muted-state">暂无资源</p>

    <div v-else class="resource-grid">
      <article v-for="resource in resources" :key="resource.id" class="resource-card">
        <div>
          <h2>{{ resource.title }}</h2>
          <p>{{ resource.description || '暂无描述' }}</p>
        </div>
        <dl>
          <div>
            <dt>类型</dt>
            <dd>{{ resource.fileType }}</dd>
          </div>
          <div>
            <dt>下载</dt>
            <dd>{{ resource.downloadCount }}</dd>
          </div>
          <div>
            <dt>版本</dt>
            <dd>v{{ resource.version }}</dd>
          </div>
        </dl>
        <RouterLink class="text-link" :to="`/resources/${resource.id}`">查看详情</RouterLink>
      </article>
    </div>

    <footer class="list-footer" aria-label="分页信息">
      共 {{ total }} 条，当前第 {{ query.page }} 页
    </footer>
  </section>
</template>
