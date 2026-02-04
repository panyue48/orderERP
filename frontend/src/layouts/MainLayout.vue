<template>
  <el-container style="min-height: 100vh">
    <el-aside width="220px" style="border-right: 1px solid var(--el-border-color)">
      <div style="padding: 16px; font-weight: 700">Order ERP</div>
      <el-menu :default-active="activePath" router>
        <MenuTree :menus="auth.menus" />
      </el-menu>
    </el-aside>
    <el-container>
      <el-header
        style="border-bottom: 1px solid var(--el-border-color); display: flex; align-items: center; gap: 12px"
      >
        <div style="font-weight: 600; flex: 1">{{ title }}</div>
        <div style="font-size: 12px; color: var(--el-text-color-secondary)">
          {{ auth.user?.nickname || auth.user?.username }}
        </div>
        <el-button size="small" @click="handleLogout">退出</el-button>
      </el-header>
      <el-main style="padding: 16px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import MenuTree from '../components/MenuTree.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activePath = computed(() => route.path)
const title = computed(() => (route.meta?.title as string) || '')

// 开发模式（HMR）或边界跳转场景下，路由守卫可能还没来得及加载菜单/权限。
// 这里兜底确保侧边栏不会“短暂空白”（除非用户确实没有任何菜单权限）。
watchEffect(() => {
  if (auth.isAuthenticated && !auth.menusLoaded) {
    auth.loadMenus().catch(() => auth.logout())
  }
  if (auth.isAuthenticated && !auth.permsLoaded) {
    auth.loadPerms().catch(() => auth.logout())
  }
})

const handleLogout = () => {
  auth.logout()
  router.push('/login')
}
</script>
