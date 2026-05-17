import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  // 👇 新增 server 配置，允许你的域名访问
  server: {
    allowedHosts: true
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
});