import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig({
  base: process.env.VITE_BASE || '/',
  server: {
    allowedHosts: true,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  // Vitest configuration (minimal setup)
  test: {
    environment: 'jsdom',
    globals: false, // use explicit imports for describe/it/expect/vi
    include: ['src/**/*.{test,spec}.{ts,js}'],
    // Ensure @ alias resolves in tests
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  }
})
