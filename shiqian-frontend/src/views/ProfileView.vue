<script setup lang="ts">
import { reactive, ref } from 'vue';
import { userApi } from '../api/user';
import { useAuthStore } from '../stores/auth';
import {
  validateOptionalAvatar,
  validateOptionalEmail,
  validateOptionalNickname,
  validateOptionalPhone
} from '../utils/validators';

const authStore = useAuthStore();
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const form = reactive({
  nickname: authStore.user?.nickname ?? '',
  email: '',
  phone: '',
  avatar: ''
});

function validateForm(): string {
  return (
    validateOptionalNickname(form.nickname) ||
    validateOptionalEmail(form.email) ||
    validateOptionalPhone(form.phone) ||
    validateOptionalAvatar(form.avatar)
  );
}

async function handleSubmit() {
  if (!authStore.isAuthenticated) {
    errorMessage.value = '请先登录';
    return;
  }

  errorMessage.value = validateForm();
  successMessage.value = '';
  if (errorMessage.value) {
    return;
  }

  loading.value = true;
  try {
    await userApi.updateCurrentUser({
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      phone: form.phone || undefined,
      avatar: form.avatar || undefined
    });
    if (authStore.user) {
      authStore.user.nickname = form.nickname || authStore.user.nickname;
    }
    successMessage.value = '资料已更新';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资料更新失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="profile-layout">
    <div class="page-panel">
      <h2>个人中心</h2>
      <template v-if="authStore.isAuthenticated && authStore.user">
        <dl class="profile-summary">
          <div>
            <dt>用户名</dt>
            <dd>{{ authStore.user.username }}</dd>
          </div>
          <div>
            <dt>昵称</dt>
            <dd>{{ authStore.user.nickname || '-' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ authStore.user.role }}</dd>
          </div>
        </dl>
      </template>
      <template v-else>
        <p>请先登录后查看和更新个人资料。</p>
        <RouterLink class="text-link" to="/login">去登录</RouterLink>
      </template>
    </div>

    <form class="form-panel form-stack" @submit.prevent="handleSubmit">
      <label>
        <span>昵称</span>
        <input v-model="form.nickname" name="nickname" />
      </label>
      <label>
        <span>邮箱</span>
        <input v-model="form.email" name="email" autocomplete="email" />
      </label>
      <label>
        <span>手机号</span>
        <input v-model="form.phone" name="phone" autocomplete="tel" />
      </label>
      <label>
        <span>头像 URL</span>
        <input v-model="form.avatar" name="avatar" />
      </label>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="loading">
        {{ loading ? '保存中' : '保存资料' }}
      </button>
    </form>
  </section>
</template>
