<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
function register() {
  store.login()
  ElMessage.success('注册成功')
  router.push('/home')
}
</script>

<template>
  <section class="auth">
    <div class="auth-visual"><h1>连接知识，共享未来。</h1><p>注册后可以发布资料、收藏资源，并跟踪审核进度。</p></div>
    <div class="auth-form">
      <h2>注册时迁账号</h2>
      <el-form label-position="top">
        <el-form-item label="邮箱 / 用户名"><el-input placeholder="请输入账号" /></el-form-item>
        <el-form-item label="密码"><el-input type="password" placeholder="请输入密码" show-password /></el-form-item>
        <el-form-item label="确认密码"><el-input type="password" placeholder="再次输入密码" show-password /></el-form-item>
        <el-button type="primary" class="full" @click="register">注册并登录</el-button>
      </el-form>
      <p class="sub">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </section>
</template>
