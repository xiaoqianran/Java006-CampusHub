<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { userApi } from '../api/user';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const errorMessage = ref('');
const form = reactive({
  username: '',
  password: ''
});

function validateForm(): string {
  if (!form.username.trim()) {
    return '用户名不能为空';
  }
  if (!form.password) {
    return '密码不能为空';
  }
  return '';
}

async function handleSubmit() {
  errorMessage.value = validateForm();
  if (errorMessage.value) {
    return;
  }

  loading.value = true;
  try {
    const result = await userApi.login(form);
    authStore.setSession(
      {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken
      },
      {
        userId: result.userId,
        username: result.username,
        nickname: result.nickname,
        role: result.role
      }
    );
    await router.push('/resources');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="form-panel" aria-labelledby="login-title">
    <div class="form-heading">
      <h2 id="login-title">登录</h2>
      <RouterLink to="/register">创建账号</RouterLink>
    </div>

    <form class="form-stack" @submit.prevent="handleSubmit">
      <label>
        <span>用户名</span>
        <input v-model="form.username" name="username" autocomplete="username" />
      </label>
      <label>
        <span>密码</span>
        <input
          v-model="form.password"
          name="password"
          type="password"
          autocomplete="current-password"
        />
      </label>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <button class="primary-button" type="submit" :disabled="loading">
        {{ loading ? '登录中' : '登录' }}
      </button>
    </form>
  </section>
</template>
