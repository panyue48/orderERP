<template>
  <div style="min-height: 100vh; display: grid; place-items: center; background: #f5f7fa">
    <el-card style="width: 360px">
      <template #header>
        <div style="font-weight: 700; font-size: 18px">Order ERP 登录</div>
      </template>
      <el-form :model="form" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="form.username" autocomplete="username" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <div style="display: flex; width: 100%; justify-content: space-between; gap: 12px">
            <el-checkbox v-model="rememberPassword">记住密码</el-checkbox>
            <el-checkbox v-model="persistLogin">保持登录状态</el-checkbox>
          </div>
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" native-type="submit" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const rememberPassword = ref(localStorage.getItem('rememberPassword') === 'true')
const persistLogin = ref(localStorage.getItem('persistLogin') === 'true')

const form = reactive({
  username: 'admin',
  password: ''
})

onMounted(() => {
  if (rememberPassword.value) {
    const u = localStorage.getItem('rememberedUsername')
    const p = localStorage.getItem('rememberedPassword')
    if (u) form.username = u
    if (p) form.password = p
  } else {
    // Dev-friendly default when not remembering.
    form.password = '123456'
  }
})

const handleLogin = async () => {
  if (loading.value) return
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password, { persistLogin: persistLogin.value })

    if (rememberPassword.value) {
      localStorage.setItem('rememberPassword', 'true')
      localStorage.setItem('rememberedUsername', form.username)
      localStorage.setItem('rememberedPassword', form.password)
    } else {
      localStorage.setItem('rememberPassword', 'false')
      localStorage.removeItem('rememberedUsername')
      localStorage.removeItem('rememberedPassword')
    }

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (error) {
    ElMessage.error('登录失败，请检查账号或密码')
  } finally {
    loading.value = false
  }
}
</script>
