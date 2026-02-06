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
  'views/WmsCheckBills.vue': () => import('../views/WmsCheckBills.vue'),
  'views/PurchaseOrders.vue': () => import('../views/PurchaseOrders.vue'),
  'views/PurchaseInbounds.vue': () => import('../views/PurchaseInbounds.vue'),
  'views/PurchaseReturns.vue': () => import('../views/PurchaseReturns.vue'),
  'views/PurchaseApBills.vue': () => import('../views/PurchaseApBills.vue'),
  'views/SalesOrders.vue': () => import('../views/SalesOrders.vue'),
  'views/SalesOutbounds.vue': () => import('../views/SalesOutbounds.vue'),
  'views/SalesReturns.vue': () => import('../views/SalesReturns.vue'),
  'views/SalesArBills.vue': () => import('../views/SalesArBills.vue'),
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

  // 根级子路由："/dashboard" -> "dashboard"
  if (!parent) return full.replace(/^\//, '')

  // 嵌套子路由："/system/users" 挂在 "/system" 下 -> "users"
  const prefix = `${parent}/`
  if (full.startsWith(prefix)) return full.slice(prefix.length)

  // 兜底：去掉前导 "/"，避免把子路由注册成“绝对子路由”。
  return full.replace(/^\//, '')
}

export function buildRoutes(menus: MenuRoute[], parentFullPath = ''): RouteRecordRaw[] {
  return menus.map((menu) => {
    const hasChildren = Boolean(menu.children && menu.children.length > 0)
    const component = hasChildren && menu.component === 'RouteView' ? RouteView : resolveComponent(menu.component)

    // 重要：在父路由（Root）下面动态加子路由时，child path 必须是相对路径；
    // 否则 Vue Router 会当成绝对路径，嵌套/布局行为会变得不一致。
    const path = toRelativeChildPath(menu.path, parentFullPath)

    const route: RouteRecordRaw = {
      path,
      name: menu.name,
      component,
      meta: menu.meta,
      children: hasChildren ? buildRoutes(menu.children ?? [], menu.path) : undefined
    }

    // 如果用户直接访问“目录型菜单”（例如点了子菜单组标题），重定向到第一个子路由，
    // 避免出现“空页面”（RouteView 下没有任何匹配的 children）。
    if (hasChildren && menu.children && menu.children.length > 0) {
      route.redirect = normalizePath(menu.children[0].path)
    }

    return route
  })
}
