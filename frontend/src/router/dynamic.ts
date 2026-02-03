import type { RouteRecordRaw } from 'vue-router'
import type { MenuRoute } from '../api/auth'
import MainLayout from '../layouts/MainLayout.vue'
import { RouteView } from './routeView'

type RouteComponent = NonNullable<RouteRecordRaw['component']>

const componentMap: Record<string, RouteComponent> = {
  Layout: MainLayout,
  RouteView,
  'views/Dashboard.vue': () => import('../views/Dashboard.vue'),
  'views/Ledger.vue': () => import('../views/Ledger.vue'),
  'views/Profile.vue': () => import('../views/Profile.vue'),
  'views/SystemUsers.vue': () => import('../views/SystemUsers.vue'),
  'views/SystemRoles.vue': () => import('../views/SystemRoles.vue'),
  'views/BaseProducts.vue': () => import('../views/BaseProducts.vue'),
  'views/BaseWarehouses.vue': () => import('../views/BaseWarehouses.vue'),
  'views/BasePartners.vue': () => import('../views/BasePartners.vue'),
  'views/BaseCategories.vue': () => import('../views/BaseCategories.vue'),
  'views/WmsStocks.vue': () => import('../views/WmsStocks.vue'),
  'views/WmsStockInBills.vue': () => import('../views/WmsStockInBills.vue'),
  'views/WmsStockOutBills.vue': () => import('../views/WmsStockOutBills.vue'),
  'views/WmsStockLogs.vue': () => import('../views/WmsStockLogs.vue'),
  'views/Placeholder.vue': () => import('../views/Placeholder.vue')
}

const resolveComponent = (component?: string | null): RouteComponent => {
  if (component && componentMap[component]) {
    return componentMap[component]
  }
  return componentMap['views/Placeholder.vue']
}

const normalizePath = (p: string) => {
  if (!p) return ''
  return p.startsWith('/') ? p : `/${p}`
}

const toRelativeChildPath = (fullPath: string, parentFullPath: string) => {
  const full = normalizePath(fullPath)
  const parent = normalizePath(parentFullPath === '/' ? '' : parentFullPath)

  // Root children: "/dashboard" -> "dashboard"
  if (!parent) return full.replace(/^\//, '')

  // Nested children: "/system/users" under "/system" -> "users"
  const prefix = `${parent}/`
  if (full.startsWith(prefix)) return full.slice(prefix.length)

  // Fallback: strip leading "/" to avoid registering an absolute child route.
  return full.replace(/^\//, '')
}

export function buildRoutes(menus: MenuRoute[], parentFullPath = ''): RouteRecordRaw[] {
  return menus.map((menu) => {
    const hasChildren = Boolean(menu.children && menu.children.length > 0)
    const component = hasChildren && menu.component === 'RouteView' ? RouteView : resolveComponent(menu.component)

    // Important: when adding routes under a parent ("Root"), child paths must be relative.
    // Otherwise Vue Router treats them as absolute and nesting/layout behavior becomes inconsistent.
    const path = toRelativeChildPath(menu.path, parentFullPath)

    const route: RouteRecordRaw = {
      path,
      name: menu.name,
      component,
      meta: menu.meta,
      children: hasChildren ? buildRoutes(menu.children ?? [], menu.path) : undefined
    }

    // If a directory route gets visited directly (e.g. clicking submenu title), redirect to first child
    // to avoid an "empty page" (RouteView with no matched children).
    if (hasChildren && menu.children && menu.children.length > 0) {
      route.redirect = normalizePath(menu.children[0].path)
    }

    return route
  })
}
