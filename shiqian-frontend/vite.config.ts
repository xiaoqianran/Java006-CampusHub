import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: true,
    // 关键：代理 /api 到网关，便于 `npm run dev` 直接联调后端（8080）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 不要 rewrite，保持 /api 前缀
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
});