import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../layouts/MainLayout.vue'
import { buildRoutes } from './dynamic'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      name: 'Root',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [{ path: '', name: 'RootIndex', redirect: '/dashboard' }]
    },
    { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/Placeholder.vue') }
  ]
})

function ensureDynamicRoutes(auth: ReturnType<typeof useAuthStore>) {
  const routes = buildRoutes(auth.menus)
  routes.forEach((route) => {
    if (route.name && !router.hasRoute(route.name)) {
      router.addRoute('Root', route)
    }
  })
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!auth.isAuthenticated && to.name !== 'Login') {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (auth.isAuthenticated && !auth.menusLoaded) {
    try {
      const menus = await auth.loadMenus()
      // menus 已经写入 auth，这里保留局部变量只是为了可读性。
      void menus
      ensureDynamicRoutes(auth)
      // 重要：这里不要对 `to` 做展开（spread）。
      // 如果当前命中的是 NotFound（因为动态路由尚未注册），展开会把 `name:'NotFound'` 带过去，
      // 导致你依然停留在占位页上。
      return { path: to.fullPath, replace: true }
    } catch (error) {
      auth.logout()
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
  }

  if (auth.isAuthenticated && to.name === 'Login') {
    return { path: '/' }
  }

  return true
})
