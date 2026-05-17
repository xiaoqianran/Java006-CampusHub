import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { createPinia } from 'pinia';

import App from './App.vue';
import { router } from './router';
import './styles.css';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';

// NProgress 极简学术风配置
NProgress.configure({ showSpinner: false, trickleSpeed: 120 });

// 创建 pinia（保留原有 stores 兼容）
const pinia = createPinia();

const app = createApp(App);

// 注册 Element 图标（可选全局）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app
  .use(pinia)
  .use(router)
  .use(ElementPlus, { size: 'default', zIndex: 3000 })
  .mount('#app');

// 路由进度条（世界级体验）
router.beforeEach(() => NProgress.start());
router.afterEach(() => NProgress.done());
