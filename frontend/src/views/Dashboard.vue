<template>
  <el-card>
    <div style="font-size: 16px; font-weight: 600; margin-bottom: 8px">MVP 状态</div>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="后端健康检查">
        <el-tag v-if="healthOk" type="success">UP</el-tag>
        <el-tag v-else type="danger">DOWN</el-tag>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../api/http'

const healthOk = ref(false)

onMounted(async () => {
  try {
    const res = await http.get('/api/health')
    healthOk.value = res.data?.status === 'UP'
  } catch {
    healthOk.value = false
  }
})
</script>

