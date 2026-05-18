<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const form = reactive({ username: '', nickname: '', email: '', phone: '', password: '', confirmPassword: '' })

async function register() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await store.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone
    })
    ElMessage.success('注册成功')
    router.push('/home')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth">
    <div class="auth-visual"><h1>连接知识，共享未来。</h1><p>注册后可以发布资料、收藏资源，并跟踪审核进度。</p></div>
    <div class="auth-form">
      <h2>注册时迁账号</h2>
      <el-form label-position="top">
        <el-form-item label="用户名"><el-input v-model="form.username" placeholder="4-20 位字母、数字或下划线" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" placeholder="不填则使用用户名" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="可选" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="可选" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="6-32 位密码" show-password /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password @keyup.enter="register" /></el-form-item>
        <el-button type="primary" class="full" :loading="submitting" @click="register">注册并登录</el-button>
      </el-form>
      <p class="sub">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </section>
</template>
