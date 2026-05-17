<script setup lang="ts">
import { reactive, ref } from 'vue';
import { userApi } from '../api/user';
import {
  validateOptionalEmail,
  validateOptionalPhone,
  validatePassword,
  validateUsername
} from '../utils/validators';

const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: ''
});

function validateForm(): string {
  return (
    validateUsername(form.username) ||
    validatePassword(form.password) ||
    (form.nickname.length > 20 ? '昵称不能超过20个字符' : '') ||
    validateOptionalEmail(form.email) ||
    validateOptionalPhone(form.phone)
  );
}

async function handleSubmit() {
  errorMessage.value = validateForm();
  successMessage.value = '';
  if (errorMessage.value) {
    return;
  }

  loading.value = true;
  try {
    await userApi.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      phone: form.phone || undefined
    });
    successMessage.value = '注册成功，请登录';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注册失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="form-panel" aria-labelledby="register-title">
    <div class="form-heading">
      <h2 id="register-title">注册</h2>
      <RouterLink to="/login">已有账号</RouterLink>
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
          autocomplete="new-password"
        />
      </label>
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

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="loading">
        {{ loading ? '提交中' : '注册' }}
      </button>
    </form>
  </section>
</template>
