import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import ACCESS_ENUM from '@/access/accessEnum.ts'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: '主页', component: HomePage },
  { path: '/user/login', name: '用户登录', component: UserLoginPage, meta: { hideInMenu: true } },
  {
    path: '/user/register',
    name: '用户注册',
    component: UserRegisterPage,
    meta: { hideInMenu: true },
  },
  {
    path: '/admin/userManage',
    name: '用户管理',
    component: UserManagePage,
    meta: { access: ACCESS_ENUM.ADMIN },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
