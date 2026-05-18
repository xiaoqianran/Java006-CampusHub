import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色变量（当 html 带有 .dark 时生效，我们同时使用 data-theme 做更强控制）
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/styles.css'

// 极早初始化主题，防止刷新时白屏闪烁
const savedTheme = (localStorage.getItem('shiqian_theme') as 'light' | 'dark' | null) || 'light'
document.documentElement.dataset.theme = savedTheme
document.documentElement.style.setProperty('color-scheme', savedTheme === 'dark' ? 'dark' : 'light')

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
