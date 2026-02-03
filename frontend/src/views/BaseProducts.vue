<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">商品管理</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索 SKU / 商品名称" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新增商品</el-button>
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
      <el-table-column label="图片" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.imageUrl"
            :src="row.imageUrl"
            style="width: 40px; height: 40px; border-radius: 6px"
            fit="cover"
            preview-teleported
          />
          <span v-else style="color: #999">-</span>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="160">
        <template #default="{ row }">
          <span>{{ (row.categoryId && categoryNameById.get(row.categoryId)) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="productCode" label="SKU" width="160" />
      <el-table-column prop="productName" label="商品名称" min-width="220" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="purchasePrice" label="参考进价" width="120" />
      <el-table-column prop="salePrice" label="标准售价" width="120" />
      <el-table-column prop="lowStock" label="预警" width="90" />
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="分类">
        <el-select v-model="form.categoryId" clearable filterable style="width: 320px" placeholder="可选">
          <el-option v-for="c in categories" :key="c.id" :label="`${c.categoryName} (${c.categoryCode})`" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="SKU">
        <el-input v-model="form.productCode" autocomplete="off" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="form.productName" autocomplete="off" />
      </el-form-item>
      <el-form-item label="单位">
        <el-input v-model="form.unit" placeholder="默认：个" style="width: 200px" />
      </el-form-item>
      <el-form-item label="参考进价">
        <el-input-number v-model="form.purchasePrice" :min="0" :precision="2" :step="1" />
      </el-form-item>
      <el-form-item label="标准售价">
        <el-input-number v-model="form.salePrice" :min="0" :precision="2" :step="1" />
      </el-form-item>
      <el-form-item label="库存预警">
        <el-input-number v-model="form.lowStock" :min="0" :step="1" />
      </el-form-item>
      <el-form-item label="图片">
        <div style="display: flex; align-items: center; gap: 12px">
          <el-upload
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="doUpload"
          >
            <el-button>上传图片</el-button>
          </el-upload>
          <el-input v-model="form.imageUrl" placeholder="或直接粘贴图片 URL" style="flex: 1" />
        </div>
        <div v-if="form.imageUrl" style="margin-top: 10px">
          <el-image :src="form.imageUrl" style="width: 80px; height: 80px; border-radius: 8px" fit="cover" />
        </div>
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
import { ElLoading, ElMessage, ElMessageBox, ElNotification, type UploadRequestOptions } from 'element-plus'
import { createProduct, deleteProduct, listCategoryOptions, listProducts, updateProduct, type BaseProduct } from '../api/base'
import { uploadImage } from '../api/files'
import { useAuthStore } from '../stores/auth'
import { baseExcel, type ImportResult } from '../api/base-excel'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('base:product:add'))
const canEdit = computed(() => auth.hasPerm('base:product:edit'))
const canRemove = computed(() => auth.hasPerm('base:product:remove'))
const canExport = computed(() => auth.hasPerm('base:product:export'))
const canImport = computed(() => auth.hasPerm('base:product:import'))

const categories = ref<{ id: number; categoryCode: string; categoryName: string }[]>([])
const categoryNameById = computed(() => {
  const m = new Map<number, string>()
  for (const c of categories.value) m.set(c.id, `${c.categoryName}`)
  return m
})

const loading = ref(false)
const saving = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<BaseProduct[]>([])
const keyword = ref('')

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const dialogTitle = computed(() => (mode.value === 'create' ? '新增商品' : '编辑商品'))

const form = reactive({
  categoryId: null as number | null,
  productCode: '',
  productName: '',
  unit: '个',
  purchasePrice: 0,
  salePrice: 0,
  lowStock: 10,
  imageUrl: '',
  status: 1
})

async function reload() {
  loading.value = true
  try {
    const data = await listProducts({ page: page.value, size: size.value, keyword: keyword.value || undefined })
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
  form.categoryId = null
  form.productCode = ''
  form.productName = ''
  form.unit = '个'
  form.purchasePrice = 0
  form.salePrice = 0
  form.lowStock = 10
  form.imageUrl = ''
  form.status = 1
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: BaseProduct) {
  mode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.categoryId = (row.categoryId as any) ?? null
  form.productCode = row.productCode
  form.productName = row.productName
  form.unit = row.unit || '个'
  form.purchasePrice = Number(row.purchasePrice ?? 0)
  form.salePrice = Number(row.salePrice ?? 0)
  form.lowStock = Number(row.lowStock ?? 10)
  form.imageUrl = row.imageUrl || ''
  form.status = Number(row.status ?? 1)
  dialogVisible.value = true
}

async function submit() {
  if (!form.productCode || !form.productName) {
    ElMessage.warning('请填写 SKU 和商品名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      categoryId: form.categoryId ?? undefined,
      productCode: form.productCode,
      productName: form.productName,
      unit: form.unit || '个',
      purchasePrice: form.purchasePrice,
      salePrice: form.salePrice,
      lowStock: form.lowStock,
      imageUrl: form.imageUrl || undefined,
      status: form.status
    }
    if (mode.value === 'create') {
      await createProduct(payload)
      ElMessage.success('已创建')
    } else {
      if (!editingId.value) return
      await updateProduct(editingId.value, payload)
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

async function remove(row: BaseProduct) {
  await ElMessageBox.confirm(`确认删除商品 ${row.productCode} 吗？`, '提示', { type: 'warning' })
  try {
    await deleteProduct(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function beforeUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  const maxMb = 5
  if (file.size > maxMb * 1024 * 1024) {
    ElMessage.error(`图片大小不能超过 ${maxMb}MB`)
    return false
  }
  return true
}

async function doUpload(options: UploadRequestOptions) {
  try {
    const file = options.file as File
    const res = await uploadImage(file)
    form.imageUrl = res.url
    options.onSuccess?.(res as any)
    ElMessage.success('上传成功')
  } catch (e: any) {
    options.onError?.(e)
    ElMessage.error(e?.response?.data?.message || '上传失败')
  }
}

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

async function doImport(options: UploadRequestOptions) {
  try {
    const file = options.file as File
    const res: ImportResult = await baseExcel.importProducts(file)
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
    await baseExcel.exportProducts(keyword.value || undefined)
    ElNotification({ title: '导出已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({
      title: '导出失败',
      message: e?.response?.data?.message || e?.message || '导出失败',
      type: 'error',
      duration: 6000
    })
  } finally {
    loadingSvc.close()
  }
}

async function downloadTemplate() {
  const loadingSvc = ElLoading.service({ lock: true, text: '正在下载模板...', background: 'rgba(0,0,0,0.15)' })
  try {
    await baseExcel.productTemplate()
    ElNotification({ title: '模板下载已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({
      title: '模板下载失败',
      message: e?.response?.data?.message || e?.message || '下载模板失败',
      type: 'error',
      duration: 6000
    })
  } finally {
    loadingSvc.close()
  }
}

onMounted(() => {
  Promise.all([
    reload().catch((e: any) => ElMessage.error(e?.response?.data?.message || '加载商品失败')),
    listCategoryOptions({ limit: 200 })
      .then((opts) => {
        categories.value = opts.map((o) => ({ id: o.id, categoryCode: o.categoryCode, categoryName: o.categoryName }))
      })
      .catch(() => {
        categories.value = []
      })
  ])
})
</script>
