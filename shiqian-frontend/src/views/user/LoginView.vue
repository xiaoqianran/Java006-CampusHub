<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })

async function login() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  submitting.value = true
  try {
    await store.login(form.username, form.password)
    ElMessage.success({ message: '登录成功', duration: 800 })
    const target = store.currentUser?.role === 'ADMIN' ? '/admin' : '/home'
    router.push(target)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth">
    <div class="auth-visual"><h1>欢迎回到时迁</h1><p>登录后学生端和后台端拥有统一入口，不再出现页面割裂。</p></div>
    <div class="auth-form">
      <h2>登录</h2>
      <el-form :model="form" label-position="top" @submit.prevent="login">
        <el-form-item label="用户名"><el-input v-model="form.username" placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="请输入密码" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" class="full" :loading="submitting">登录</el-button>
      </el-form>
      <p class="sub">没有账号？<router-link to="/register">去注册</router-link></p>
    </div>
  </section>
</template>
