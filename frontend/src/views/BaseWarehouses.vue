<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">仓库管理</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索 仓库编码 / 名称" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新增仓库</el-button>
          <el-button v-if="canExport" @click="doExport">导出</el-button>
          <el-upload v-if="canImport" :show-file-list="false" :before-upload="beforeImport" :http-request="doImport">
            <el-button>导入</el-button>
          </el-upload>
          <el-button v-if="canExport" @click="downloadTemplate">模板</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="warehouseCode" label="编码" width="160" />
      <el-table-column prop="warehouseName" label="名称" min-width="220" />
      <el-table-column prop="location" label="地址" min-width="240" />
      <el-table-column prop="manager" label="负责人" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canRemove" link type="danger" @click="remove(row)">删除</el-button>
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="编码">
        <el-input v-model="form.warehouseCode" autocomplete="off" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="form.warehouseName" autocomplete="off" />
      </el-form-item>
      <el-form-item label="地址">
        <el-input v-model="form.location" placeholder="可选" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="form.manager" placeholder="可选" style="width: 220px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElLoading, ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  createWarehouse,
  deleteWarehouse,
  listWarehouses,
  updateWarehouse,
  type BaseWarehouse
} from '../api/base'
import { useAuthStore } from '../stores/auth'
import { baseExcel, type ImportResult } from '../api/base-excel'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('base:warehouse:add'))
const canEdit = computed(() => auth.hasPerm('base:warehouse:edit'))
const canRemove = computed(() => auth.hasPerm('base:warehouse:remove'))
const canExport = computed(() => auth.hasPerm('base:warehouse:export'))
const canImport = computed(() => auth.hasPerm('base:warehouse:import'))

const loading = ref(false)
const saving = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<BaseWarehouse[]>([])
const keyword = ref('')

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const dialogTitle = computed(() => (mode.value === 'create' ? '新增仓库' : '编辑仓库'))

const form = reactive({
  warehouseCode: '',
  warehouseName: '',
  location: '',
  manager: '',
  status: 1
})

function errText(e: any, fallback: string) {
  return e?.response?.data?.message || e?.message || fallback
}

async function reload() {
  loading.value = true
  try {
    const data = await listWarehouses({ page: page.value, size: size.value, keyword: keyword.value || undefined })
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

function resetForm() {
  form.warehouseCode = ''
  form.warehouseName = ''
  form.location = ''
  form.manager = ''
  form.status = 1
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: BaseWarehouse) {
  mode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.warehouseCode = row.warehouseCode
  form.warehouseName = row.warehouseName
  form.location = row.location || ''
  form.manager = row.manager || ''
  form.status = Number(row.status ?? 1)
  dialogVisible.value = true
}

async function submit() {
  if (!form.warehouseCode || !form.warehouseName) {
    ElMessage.warning('请填写编码和名称')
    return
  }

  saving.value = true
  try {
    const payload = {
      warehouseCode: form.warehouseCode,
      warehouseName: form.warehouseName,
      location: form.location || undefined,
      manager: form.manager || undefined,
      status: form.status
    }

    if (mode.value === 'create') {
      await createWarehouse(payload)
      ElMessage.success('已创建')
    } else {
      if (!editingId.value) return
      await updateWarehouse(editingId.value, payload)
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

async function remove(row: BaseWarehouse) {
  await ElMessageBox.confirm(`确认删除仓库 ${row.warehouseCode} 吗？`, '提示', { type: 'warning' })
  try {
    await deleteWarehouse(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  reload().catch((e: any) => ElMessage.error(e?.response?.data?.message || '加载仓库失败'))
})

function beforeImport(file: File) {
  const ok =
    file.name.toLowerCase().endsWith('.xlsx') ||
    file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  if (!ok) {
    ElMessage.error('请上传 .xlsx 文件')
    return false
  }
  return true
}

async function doImport(options: any) {
  try {
    const file = options.file as File
    const res: ImportResult = await baseExcel.importWarehouses(file)
    options.onSuccess?.(res as any)
    if (res.failed > 0) {
      ElMessage.warning(`导入完成：成功 ${res.inserted + res.updated}，失败 ${res.failed}`)
    } else {
      ElMessage.success(`导入完成：新增 ${res.inserted}，更新 ${res.updated}`)
    }
    await reload()
  } catch (e: any) {
    options.onError?.(e)
    ElMessage.error(e?.response?.data?.message || '导入失败')
  }
}

async function doExport() {
  const loadingSvc = ElLoading.service({ lock: true, text: '正在生成导出文件...', background: 'rgba(0,0,0,0.15)' })
  try {
    await baseExcel.exportWarehouses(keyword.value || undefined)
    ElNotification({ title: '导出已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({ title: '导出失败', message: errText(e, '导出失败'), type: 'error', duration: 5000 })
  } finally {
    loadingSvc.close()
  }
}

async function downloadTemplate() {
  const loadingSvc = ElLoading.service({ lock: true, text: '正在下载模板...', background: 'rgba(0,0,0,0.15)' })
  try {
    await baseExcel.warehouseTemplate()
    ElNotification({ title: '模板下载已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({ title: '模板下载失败', message: errText(e, '模板下载失败'), type: 'error', duration: 5000 })
  } finally {
    loadingSvc.close()
  }
}
</script>
