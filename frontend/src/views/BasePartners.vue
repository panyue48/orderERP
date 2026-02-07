<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">往来单位</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索 编码 / 名称" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新增单位</el-button>
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
      <el-table-column prop="partnerCode" label="编码" width="160" />
      <el-table-column prop="partnerName" label="名称" min-width="220" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.type === 1">供应商</el-tag>
          <el-tag v-else type="success">客户</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contact" label="联系人" width="140" />
      <el-table-column prop="phone" label="电话" width="150" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="creditLimit" label="信用额度" width="120" />
      <el-table-column label="已占用额度" width="120">
        <template #default="{ row }">
          <span v-if="row.type === 2">{{ fmtMoney(creditUsageById[row.id]?.usedAmount) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="可用额度" width="120">
        <template #default="{ row }">
          <span v-if="row.type === 2">{{ fmtMoney(creditUsageById[row.id]?.availableAmount) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="编码">
        <el-input v-model="form.partnerCode" autocomplete="off" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="form.partnerName" autocomplete="off" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.type" style="width: 200px">
          <el-option :value="1" label="供应商" />
          <el-option :value="2" label="客户" />
        </el-select>
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="form.contact" placeholder="可选" />
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="form.phone" placeholder="可选" style="width: 240px" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" placeholder="可选" />
      </el-form-item>
      <el-form-item label="信用额度">
        <el-input-number v-model="form.creditLimit" :min="0" :precision="2" :step="100" />
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
import { createPartner, deletePartner, listPartners, updatePartner, type BasePartner } from '../api/base'
import { baseExcel, type ImportResult } from '../api/base-excel'
import { listSalesCustomerCreditUsage, type SalCreditUsage } from '../api/sales'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('base:partner:add'))
const canEdit = computed(() => auth.hasPerm('base:partner:edit'))
const canRemove = computed(() => auth.hasPerm('base:partner:remove'))
const canExport = computed(() => auth.hasPerm('base:partner:export'))
const canImport = computed(() => auth.hasPerm('base:partner:import'))

const loading = ref(false)
const saving = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<BasePartner[]>([])
const keyword = ref('')
const creditUsageById = ref<Record<number, SalCreditUsage>>({})

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const dialogTitle = computed(() => (mode.value === 'create' ? '新增往来单位' : '编辑往来单位'))

const form = reactive({
  partnerName: '',
  partnerCode: '',
  type: 1,
  contact: '',
  phone: '',
  email: '',
  creditLimit: 0,
  status: 1
})

function errText(e: any, fallback: string) {
  return e?.response?.data?.message || e?.message || fallback
}

function fmtMoney(v: any) {
  if (v === null || v === undefined) return '-'
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return n.toFixed(2).replace(/\.00$/, '')
}

async function reload() {
  loading.value = true
  try {
    const data = await listPartners({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = data.content
    total.value = data.totalElements
    creditUsageById.value = {}
    const customerIds = (rows.value || []).filter((r) => r.type === 2 && r.id).map((r) => Number(r.id))
    if (customerIds.length > 0) {
      try {
        const usageRows = await listSalesCustomerCreditUsage(customerIds)
        const m: Record<number, SalCreditUsage> = {}
        for (const u of usageRows || []) {
          if (!u?.customerId) continue
          m[Number(u.customerId)] = u
        }
        creditUsageById.value = m
      } catch {
        creditUsageById.value = {}
      }
    }
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function resetForm() {
  form.partnerName = ''
  form.partnerCode = ''
  form.type = 1
  form.contact = ''
  form.phone = ''
  form.email = ''
  form.creditLimit = 0
  form.status = 1
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: BasePartner) {
  mode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.partnerName = row.partnerName
  form.partnerCode = row.partnerCode
  form.type = Number(row.type ?? 1)
  form.contact = row.contact || ''
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.creditLimit = Number(row.creditLimit ?? 0)
  form.status = Number(row.status ?? 1)
  dialogVisible.value = true
}

async function submit() {
  if (!form.partnerCode || !form.partnerName) {
    ElMessage.warning('请填写编码和名称')
    return
  }

  saving.value = true
  try {
    const payload = {
      partnerName: form.partnerName,
      partnerCode: form.partnerCode,
      type: form.type,
      contact: form.contact || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      creditLimit: form.creditLimit,
      status: form.status
    }

    if (mode.value === 'create') {
      await createPartner(payload)
      ElMessage.success('已创建')
    } else {
      if (!editingId.value) return
      await updatePartner(editingId.value, payload)
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

async function remove(row: BasePartner) {
  await ElMessageBox.confirm(`确认删除 ${row.partnerCode} 吗？`, '提示', { type: 'warning' })
  try {
    await deletePartner(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  reload().catch((e: any) => ElMessage.error(e?.response?.data?.message || '加载往来单位失败'))
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
    const res: ImportResult = await baseExcel.importPartners(file)
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
    await baseExcel.exportPartners(keyword.value || undefined)
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
    await baseExcel.partnerTemplate()
    ElNotification({ title: '模板下载已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({ title: '模板下载失败', message: errText(e, '模板下载失败'), type: 'error', duration: 5000 })
  } finally {
    loadingSvc.close()
  }
}
</script>
