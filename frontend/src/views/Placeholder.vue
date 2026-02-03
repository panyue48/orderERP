<template>
  <div style="padding: 24px; background: #fff; border-radius: 8px">
    <div style="font-size: 18px; font-weight: 600">{{ title }}</div>
    <div style="margin-top: 8px; color: var(--el-text-color-secondary)">该页面处于第一阶段占位状态。</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta?.title as string) || '功能页面')
</script>
