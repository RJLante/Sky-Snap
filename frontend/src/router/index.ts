import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import ACCESS_ENUM from '@/access/accessEnum.ts'
import AddPicturePage from '@/pages/AddPicturePage.vue'
import PictureManagePage from '@/pages/admin/PictureManagePage.vue'
import PictureDetailPage from '@/pages/PictureDetailPage.vue'
import AddPictureBatchPage from '@/pages/AddPictureBatchPage.vue'
import SpaceManagePage from '@/pages/admin/SpaceManagePage.vue'
import AddSpacePage from '@/pages/AddSpacePage.vue'
import MySpacePage from '@/pages/MySpacePage.vue'
import SpaceDetailPage from '@/pages/SpaceDetailPage.vue'
import SearchPicturePage from '@/pages/SearchPicturePage.vue'
import SpaceAnalyzePage from '@/pages/SpaceAnalyzePage.vue'
import SpaceUserManagePage from '@/pages/admin/SpaceUserManagePage.vue'
import UserInfoPage from '@/pages/user/UserInfoPage.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: '主页', component: HomePage },
  {
    path: '/add_picture',
    name: '创建图片',
    component: AddPicturePage
  },
  {
    path: '/add_space',
    name: '创建空间',
    component: AddSpacePage
  },
  {
    path: '/my_space',
    name: '我的空间',
    component: MySpacePage,
    meta: { hideInMenu: true }
  },
  {
    path: '/user/info/:id',
    name: '个人信息',
    component: UserInfoPage,
    props: true,
    meta: { hideInMenu: true }
  },
  {
    path: '/add_picture/batch',
    name: '批量创建图片',
    component: AddPictureBatchPage,
    meta: { hideInMenu: true }
  },
  { path: '/user/login', name: '用户登录', component: UserLoginPage, meta: { hideInMenu: true } },
  {
    path: '/user/register',
    name: '用户注册',
    component: UserRegisterPage,
    meta: { hideInMenu: true }
  },
  {
    path: '/admin/userManage',
    name: '用户管理',
    component: UserManagePage,
    meta: { access: ACCESS_ENUM.ADMIN }
  },
  {
    path: '/admin/pictureManage',
    name: '图片管理',
    component: PictureManagePage,
    meta: { access: ACCESS_ENUM.ADMIN }
  },
  {
    path: '/admin/spaceManage',
    name: '空间管理',
    component: SpaceManagePage,
    meta: { access: ACCESS_ENUM.ADMIN }
  },
  {
    path: '/spaceUserManage/:id',
    name: '空间成员管理',
    component: SpaceUserManagePage,
    meta: { access: ACCESS_ENUM.ADMIN , hideInMenu: true},
    props: true,
  },
  {
    path: '/picture/:id',
    name: '图片详情',
    component: PictureDetailPage,
    props: true,
    meta: { hideInMenu: true }
  },
  {
    path: '/space/:id',
    name: '空间详情',
    component: SpaceDetailPage,
    props: true,
    meta: { hideInMenu: true }
  },
  {
    path: '/search_picture',
    name: '图片搜索',
    component: SearchPicturePage,
    meta: { hideInMenu: true }
  },
  {
    path: '/space_analyze',
    name: '空间分析',
    component: SpaceAnalyzePage,
    meta: { hideInMenu: true }
  },



]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router
