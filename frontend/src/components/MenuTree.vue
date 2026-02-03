<template>
  <template v-for="item in menus" :key="item.path">
    <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
      <template #title>
        <span>{{ item.meta.title }}</span>
      </template>
      <MenuTree :menus="item.children" />
    </el-sub-menu>
    <el-menu-item v-else :index="item.path">
      {{ item.meta.title }}
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { MenuRoute } from '../api/auth'

defineOptions({ name: 'MenuTree' })

defineProps<{
  menus: MenuRoute[]
}>()
</script>
