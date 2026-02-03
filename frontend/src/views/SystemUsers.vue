<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">用户管理</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索用户名/昵称" style="width: 220px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="info">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="k in row.roleKeys" :key="k" style="margin-right: 6px" size="small">{{ k }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 12px">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page + 1"
        @current-change="onPageChange"
      />
    </div>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="账号" v-if="mode === 'create'">
        <el-input v-model="form.username" autocomplete="off" />
      </el-form-item>
      <el-form-item label="密码" v-if="mode === 'create'">
        <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
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
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.roleIds" multiple filterable style="width: 100%" placeholder="选择角色">
          <el-option v-for="r in allRoles" :key="r.id" :label="`${r.roleName} (${r.roleKey})`" :value="r.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ mode === 'create' ? '创建' : '保存' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  updateUser,
  type AdminRole,
  type AdminUser
} from '../api/system-admin'

const loading = ref(false)
const saving = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<AdminUser[]>([])
const keyword = ref('')

const allRoles = ref<AdminRole[]>([])

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const dialogTitle = computed(() => (mode.value === 'create' ? '新增用户' : '编辑用户'))

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  status: 1,
  roleIds: [] as number[]
})

async function reload() {
  loading.value = true
  try {
    const data = await listUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const data = await listRoles({ page: 0, size: 200 })
  allRoles.value = data.content
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.email = ''
  form.phone = ''
  form.avatar = ''
  form.status = 1
  form.roleIds = []
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AdminUser) {
  mode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.nickname = row.nickname ?? ''
  form.email = row.email ?? ''
  form.phone = row.phone ?? ''
  form.avatar = row.avatar ?? ''
  form.status = row.status ?? 1
  form.roleIds = [...(row.roleIds ?? [])]
  dialogVisible.value = true
}

async function submit() {
  if (mode.value === 'create') {
    if (!form.username || !form.password) {
      ElMessage.warning('请输入账号和密码')
      return
    }
  }

  saving.value = true
  try {
    if (mode.value === 'create') {
      await createUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        email: form.email || undefined,
        phone: form.phone || undefined,
        avatar: form.avatar || undefined,
        status: form.status,
        roleIds: form.roleIds
      })
      ElMessage.success('已创建')
    } else {
      if (!editingId.value) return
      await updateUser(editingId.value, {
        nickname: form.nickname || undefined,
        email: form.email || undefined,
        phone: form.phone || undefined,
        avatar: form.avatar || undefined,
        status: form.status,
        roleIds: form.roleIds
      })
      ElMessage.success('已保存')
    }

    dialogVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: AdminUser) {
  await ElMessageBox.confirm(`确认删除用户 ${row.username}？`, '提示', { type: 'warning' })
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(async () => {
  try {
    await loadRoles()
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载用户数据失败')
  }
})
</script>
