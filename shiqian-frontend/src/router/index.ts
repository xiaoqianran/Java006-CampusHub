import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../stores/auth';

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { title: '时迁 - 校园资源共享平台' }
  },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
  {
    path: '/resources',
    name: 'resources',
    component: () => import('../views/ResourceListView.vue'),
    meta: { title: '资源广场' }
  },
  {
    path: '/resources/:id',
    name: 'resourceDetail',
    component: () => import('../views/ResourceDetailView.vue'),
    props: true,
    meta: { title: '资源详情' }
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('../views/PublishView.vue'),
    meta: { requiresAuth: true, title: '发布新资源' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/admin/audit',
    name: 'adminAudit',
    component: () => import('../views/AdminAuditView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '内容审核中心' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('../views/NotFoundView.vue')
  }
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0, behavior: 'smooth' };
  }
});

// 全局路由守卫 + 标题 + 认证
router.beforeEach((to, _from, next) => {
  document.title = (to.meta.title as string) || '时迁校园资源共享平台';

  const auth = useAuthStore();
  auth.hydrateFromStorage();

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } });
    return;
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    next({ name: 'home' });
    return;
  }
  next();
});
