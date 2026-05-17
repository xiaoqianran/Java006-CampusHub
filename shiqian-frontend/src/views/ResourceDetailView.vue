<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { resourceApi } from '../api/resource';
import type { ResourceItem } from '../types/resource';

const props = defineProps<{
  id: string;
}>();

const loading = ref(false);
const downloading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const resource = ref<ResourceItem | null>(null);

function parseResourceId(): number | null {
  const id = Number(props.id);
  return Number.isInteger(id) && id > 0 ? id : null;
}

async function loadDetail() {
  const id = parseResourceId();
  if (!id) {
    errorMessage.value = '资源ID不合法';
    return;
  }

  loading.value = true;
  errorMessage.value = '';
  try {
    resource.value = await resourceApi.getResource(id);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资源详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function handleDownload() {
  const id = parseResourceId();
  if (!id) {
    errorMessage.value = '资源ID不合法';
    return;
  }

  downloading.value = true;
  successMessage.value = '';
  try {
    await resourceApi.downloadResource(id);
    successMessage.value = '下载统计已更新';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '下载失败';
  } finally {
    downloading.value = false;
  }
}

onMounted(loadDetail);
</script>

<template>
  <section class="detail-page">
    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="loading" class="muted-state">资源详情加载中</p>

    <article v-else-if="resource" class="detail-panel">
      <header>
        <h2>{{ resource.title }}</h2>
        <span>v{{ resource.version }}</span>
      </header>
      <p>{{ resource.description || '暂无描述' }}</p>
      <dl class="profile-summary">
        <div>
          <dt>文件类型</dt>
          <dd>{{ resource.fileType }}</dd>
        </div>
        <div>
          <dt>文件大小</dt>
          <dd>{{ resource.fileSize }} B</dd>
        </div>
        <div>
          <dt>下载次数</dt>
          <dd>{{ resource.downloadCount }}</dd>
        </div>
      </dl>
      <a class="text-link" :href="resource.fileUrl" target="_blank" rel="noreferrer">打开文件</a>
      <button class="primary-button" type="button" :disabled="downloading" @click="handleDownload">
        {{ downloading ? '处理中' : '记录下载' }}
      </button>
      <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
    </article>
  </section>
</template>
