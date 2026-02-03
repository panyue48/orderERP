<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
            <div style="font-weight: 600">角色管理</div>
            <div style="display: flex; gap: 8px; align-items: center">
              <el-input
                v-model="keyword"
                placeholder="搜索角色名称/标识"
                style="width: 220px"
                clearable
                @keyup.enter="reload"
              />
              <el-button @click="reload">查询</el-button>
              <el-button type="primary" @click="openCreate">新增角色</el-button>
            </div>
          </div>
        </template>

        <el-table :data="rows" style="width: 100%" :loading="loading" @row-click="selectRole">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="roleName" label="角色名称" min-width="160" />
          <el-table-column prop="roleKey" label="角色标识" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success">启用</el-tag>
              <el-tag v-else type="info">禁用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
              <el-button link @click.stop="openPerms(row)">权限</el-button>
              <el-button link type="danger" @click.stop="remove(row)">删除</el-button>
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
    </el-col>

    <el-col :span="12">
      <el-card>
        <template #header>
          <div style="font-weight: 600">权限分配</div>
        </template>
        <div v-if="!selectedRole" style="color: var(--el-text-color-secondary)">点击左侧角色行，查看/编辑权限。</div>
        <div v-else>
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
            <div style="font-weight: 600">{{ selectedRole.roleName }} ({{ selectedRole.roleKey }})</div>
            <el-button type="primary" :loading="savingPerms" @click="savePerms">保存权限</el-button>
          </div>
          <el-tree
            ref="treeRef"
            :data="menuTree"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="{ children: 'children', label: 'label' }"
          />
        </div>
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="角色名称">
        <el-input v-model="form.roleName" />
      </el-form-item>
      <el-form-item label="角色标识">
        <el-input v-model="form.roleKey" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
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
  createRole,
  deleteRole,
  getRoleMenus,
  listMenus,
  listRoles,
  updateRole,
  updateRoleMenus,
  type AdminMenu,
  type AdminRole
} from '../api/system-admin'

const loading = ref(false)
const saving = ref(false)
const savingPerms = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<AdminRole[]>([])
const keyword = ref('')

const selectedRole = ref<AdminRole | null>(null)
const allMenus = ref<AdminMenu[]>([])
const treeRef = ref<any>()

type TreeNode = { id: number; label: string; children?: TreeNode[] }
const menuTree = computed<TreeNode[]>(() => buildMenuTree(allMenus.value))

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const dialogTitle = computed(() => (mode.value === 'create' ? '新增角色' : '编辑角色'))

const form = reactive({
  roleName: '',
  roleKey: '',
  sort: 0,
  status: 1
})

function resetForm() {
  form.roleName = ''
  form.roleKey = ''
  form.sort = 0
  form.status = 1
}

async function reload() {
  loading.value = true
  try {
    const data = await listRoles({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AdminRole) {
  mode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.roleName = row.roleName
  form.roleKey = row.roleKey
  form.sort = row.sort ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function submit() {
  if (!form.roleName || !form.roleKey) {
    ElMessage.warning('请填写角色名称和标识')
    return
  }
  saving.value = true
  try {
    if (mode.value === 'create') {
      await createRole({ roleName: form.roleName, roleKey: form.roleKey, sort: form.sort, status: form.status })
      ElMessage.success('已创建')
    } else {
      if (!editingId.value) return
      await updateRole(editingId.value, {
        roleName: form.roleName,
        roleKey: form.roleKey,
        sort: form.sort,
        status: form.status
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

async function remove(row: AdminRole) {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '提示', { type: 'warning' })
  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    if (selectedRole.value?.id === row.id) {
      selectedRole.value = null
    }
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function buildMenuTree(menus: AdminMenu[]): TreeNode[] {
  const nodes = menus
    // In sys_menu, function/button perms are stored as menu_type='F' and are usually marked visible=0.
    // They should still appear in the role permission tree, otherwise admins can't assign button/export/reverse perms.
    .filter((m) => m.visible !== 0 || (m.menuType || '').toUpperCase() === 'F')
    .map((m) => ({
      id: m.id,
      parentId: m.parentId ?? 0,
      label: `${m.menuName}${m.perms ? ` (${m.perms})` : ''}`
    }))

  const byParent = new Map<number, any[]>()
  for (const n of nodes) {
    const p = n.parentId ?? 0
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(n)
  }

  const build = (parentId: number): TreeNode[] => {
    const children = byParent.get(parentId) ?? []
    children.sort((a, b) => a.id - b.id)
    return children.map((c) => {
      const next = build(c.id)
      return next.length ? { id: c.id, label: c.label, children: next } : { id: c.id, label: c.label }
    })
  }

  return build(0)
}

async function selectRole(row: AdminRole) {
  selectedRole.value = row
  await loadRoleMenus(row)
}

async function openPerms(row: AdminRole) {
  await selectRole(row)
}

async function loadRoleMenus(role: AdminRole) {
  const ids = await getRoleMenus(role.id)
  // Wait a tick for tree render; then set checked keys.
  setTimeout(() => {
    treeRef.value?.setCheckedKeys(ids, false)
  }, 0)
}

async function savePerms() {
  if (!selectedRole.value) return
  const checked = (treeRef.value?.getCheckedKeys(false) as number[]) || []
  const half = (treeRef.value?.getHalfCheckedKeys() as number[]) || []
  const menuIds = Array.from(new Set([...checked, ...half])).sort((a, b) => a - b)
  savingPerms.value = true
  try {
    await updateRoleMenus(selectedRole.value.id, menuIds)
    ElMessage.success('权限已保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingPerms.value = false
  }
}

onMounted(async () => {
  try {
    allMenus.value = await listMenus()
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载角色数据失败')
  }
})
</script>
