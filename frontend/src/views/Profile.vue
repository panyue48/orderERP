<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div style="font-weight: 600">基本信息</div>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
          </div>
        </template>

        <el-form :model="form" label-width="90px">
          <el-form-item label="账号">
            <el-input v-model="form.username" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="可选" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="可选" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="可选" />
          </el-form-item>
          <el-form-item label="头像URL">
            <el-input v-model="form.avatar" placeholder="可选" />
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="12">
      <el-card>
        <template #header>
          <div style="font-weight: 600">修改密码</div>
        </template>
        <el-form :model="pwd" label-width="90px">
          <el-form-item label="原密码">
            <el-input v-model="pwd.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwd.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="changing" @click="changePassword">更新密码</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changeMyPassword, getMyProfile, updateMyProfile } from '../api/user'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const changing = ref(false)

const form = reactive({
  id: 0,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

const pwd = reactive({
  oldPassword: '',
  newPassword: ''
})

const load = async () => {
  const profile = await getMyProfile()
  form.id = profile.id
  form.username = profile.username
  form.nickname = profile.nickname ?? ''
  form.email = profile.email ?? ''
  form.phone = profile.phone ?? ''
  form.avatar = profile.avatar ?? ''
}

const saveProfile = async () => {
  saving.value = true
  try {
    const profile = await updateMyProfile({
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      phone: form.phone || undefined,
      avatar: form.avatar || undefined
    })
    // keep header nickname in sync without forcing a re-login
    auth.user = { ...(auth.user as any), nickname: profile.nickname ?? null }
    if (auth.persistLogin) {
      localStorage.setItem('user', JSON.stringify(auth.user))
    }
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

const changePassword = async () => {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  changing.value = true
  try {
    await changeMyPassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    pwd.oldPassword = ''
    pwd.newPassword = ''
    ElMessage.success('密码已更新')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '更新失败')
  } finally {
    changing.value = false
  }
}

onMounted(() => {
  load().catch(() => ElMessage.error('加载用户信息失败'))
})
</script>
