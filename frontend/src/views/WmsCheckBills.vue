<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">盘点单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新建盘点单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="billNo" label="单号" width="220" />
      <el-table-column prop="warehouseName" label="仓库" min-width="160" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="success">已完成</el-tag>
          <el-tag v-else type="warning">待执行</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="executeTime" label="执行时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-popconfirm
            v-if="canExecute && row.status === 1"
            :title="`确认执行盘点单 ${row.billNo} 吗？执行后将按实盘数量自动生成调整单并修改库存。`"
            confirm-button-text="执行"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="420"
            @confirm="execute(row.id)"
          >
            <template #reference>
              <el-button link type="danger" :loading="executingId === row.id">执行</el-button>
            </template>
          </el-popconfirm>
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

  <el-dialog v-model="detailVisible" title="盘点单详情" width="980px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="单号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.status === 2" type="success">已完成</el-tag>
          <el-tag v-else type="warning">待执行</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detail.executeTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="countedQty" label="实盘数量" width="120" />
        <el-table-column prop="bookQty" label="账面数量" width="120" />
        <el-table-column prop="diffQty" label="差异" width="120" />
      </el-table>
    </div>
  </el-dialog>

  <el-dialog v-model="createVisible" title="新建盘点单" width="980px">
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="仓库">
        <el-select v-model="createForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 320px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 8px 0">
        <div style="font-weight: 600">实盘明细</div>
        <el-button @click="addLine">添加一行</el-button>
      </div>

      <el-table :data="createForm.lines" border size="small">
        <el-table-column label="商品" min-width="420">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              filterable
              remote
              clearable
              placeholder="搜索 SKU/名称"
              style="width: 100%"
              :remote-method="searchProducts"
              :loading="productLoading"
            >
              <el-option v-for="p in productOptions" :key="p.id" :label="`${p.productName} (${p.productCode})`" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="实盘数量" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.countedQty" :min="0" :step="1" :precision="3" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="createForm.lines.length <= 1" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-form>

    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  createCheckBill,
  executeCheckBill,
  getCheckBill,
  listCheckBills,
  listProductOptions,
  listWarehouseOptions,
  type ProductOption,
  type WarehouseOption,
  type WmsCheckBill,
  type WmsCheckBillDetail
} from '../api/wms'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('wms:check:add'))
const canExecute = computed(() => auth.hasPerm('wms:check:execute'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<WmsCheckBill[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const warehouses = ref<WarehouseOption[]>([])

const createVisible = ref(false)
const saving = ref(false)
const createForm = ref<{ warehouseId?: number; remark?: string; lines: { productId?: number; countedQty: number }[] }>({
  warehouseId: undefined,
  remark: '',
  lines: [{ productId: undefined, countedQty: 0 }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<WmsCheckBillDetail | null>(null)
const executingId = ref<number | null>(null)

async function reload() {
  loading.value = true
  try {
    const res = await listCheckBills({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载盘点单失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  createForm.value = { warehouseId: undefined, remark: '', lines: [{ productId: undefined, countedQty: 0 }] }
  createVisible.value = true
  searchProducts('')
}

function addLine() {
  createForm.value.lines.push({ productId: undefined, countedQty: 0 })
}

function removeLine(idx: number) {
  createForm.value.lines.splice(idx, 1)
}

async function searchProducts(query: string) {
  productLoading.value = true
  try {
    productOptions.value = await listProductOptions({ keyword: query || undefined, limit: 200 })
  } catch {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

async function submitCreate() {
  if (!createForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  const lines = createForm.value.lines
    .filter((l) => l.productId != null)
    .map((l) => ({ productId: l.productId as number, countedQty: Number(l.countedQty) }))
  if (!lines.length) {
    ElMessage.warning('请至少选择 1 个商品')
    return
  }

  saving.value = true
  try {
    await createCheckBill({ warehouseId: createForm.value.warehouseId, remark: createForm.value.remark || undefined, lines })
    ElMessage.success('已创建')
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function execute(id: number) {
  executingId.value = id
  try {
    const res = await executeCheckBill(id)
    const adjust = res.stockInBillNo || res.stockOutBillNo
    ElNotification({
      title: '执行成功',
      message: adjust ? `已生成调整单：${adjust}` : '无需调整单（账面与实盘一致）',
      type: 'success',
      duration: 3500
    })
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getCheckBill(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    reload(),
    listWarehouseOptions({ limit: 200 })
      .then((ws) => {
        warehouses.value = ws
      })
      .catch(() => {
        warehouses.value = []
      })
  ])
})
</script>

