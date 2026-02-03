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
      // menus already stored in auth; keep `menus` only for readability
      void menus
      ensureDynamicRoutes(auth)
      // Important: do NOT spread `to` here.
      // If the current match is NotFound (because routes weren't added yet),
      // spreading keeps `name: 'NotFound'` and you'll stay on the placeholder page.
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
