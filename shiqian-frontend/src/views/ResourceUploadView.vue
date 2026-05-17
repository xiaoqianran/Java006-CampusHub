<script setup lang="ts">
import { reactive, ref } from 'vue';
import { resourceApi } from '../api/resource';
import { validateResourceCreate } from '../utils/resourceValidators';

const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const form = reactive({
  title: '',
  description: '',
  categoryId: 0,
  fileUrl: '',
  fileSize: 0,
  fileType: ''
});

async function handleSubmit() {
  errorMessage.value = validateResourceCreate(form);
  successMessage.value = '';
  if (errorMessage.value) {
    return;
  }

  loading.value = true;
  try {
    await resourceApi.createResource({
      title: form.title,
      description: form.description || undefined,
      categoryId: form.categoryId,
      fileUrl: form.fileUrl,
      fileSize: form.fileSize,
      fileType: form.fileType
    });
    successMessage.value = '资源已提交审核';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资源上传失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="form-panel upload-panel" aria-labelledby="upload-title">
    <div class="form-heading">
      <h2 id="upload-title">资源上传</h2>
      <RouterLink to="/resources">返回列表</RouterLink>
    </div>

    <form class="form-stack" @submit.prevent="handleSubmit">
      <label>
        <span>标题</span>
        <input v-model="form.title" name="title" />
      </label>
      <label>
        <span>描述</span>
        <textarea v-model="form.description" name="description" rows="4" />
      </label>
      <label>
        <span>分类 ID</span>
        <input v-model.number="form.categoryId" name="categoryId" type="number" min="1" />
      </label>
      <label>
        <span>文件地址</span>
        <input v-model="form.fileUrl" name="fileUrl" />
      </label>
      <label>
        <span>文件大小（字节）</span>
        <input v-model.number="form.fileSize" name="fileSize" type="number" min="0" />
      </label>
      <label>
        <span>文件类型</span>
        <input v-model="form.fileType" name="fileType" placeholder="application/pdf" />
      </label>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="loading">
        {{ loading ? '提交中' : '提交资源' }}
      </button>
    </form>
  </section>
</template>
