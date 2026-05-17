import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import HomeView from '../views/HomeView.vue';

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/resources',
    name: 'resources',
    component: () => import('../views/ResourceListView.vue')
  },
  {
    path: '/resources/upload',
    name: 'resourceUpload',
    component: () => import('../views/ResourceUploadView.vue')
  },
  {
    path: '/resources/:id',
    name: 'resourceDetail',
    component: () => import('../views/ResourceDetailView.vue'),
    props: true
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue')
  }
];

export const router = createRouter({
  history: createWebHistory(),
  routes
});
