<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购退货单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号/供应商/WMS单号" style="width: 260px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新建退货单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="returnNo" label="单号" width="220" />
      <el-table-column prop="returnDate" label="日期" width="120" />
      <el-table-column prop="supplierName" label="供应商" min-width="180" />
      <el-table-column prop="warehouseName" label="仓库" min-width="140" />
      <el-table-column prop="totalAmount" label="总额" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="row.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="wmsBillNo" label="WMS单号" width="190" />
      <el-table-column prop="createBy" label="制单人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>

          <el-popconfirm
            v-if="canAudit && row.status === 1"
            title="确认审核该退货单吗？"
            confirm-button-text="审核"
            cancel-button-text="取消"
            confirm-button-type="primary"
            width="280"
            @confirm="audit(row.id)"
          >
            <template #reference>
              <el-button link type="primary" :loading="auditingId === row.id">审核</el-button>
            </template>
          </el-popconfirm>

          <el-popconfirm
            v-if="canExecute && row.status === 2"
            :title="`确认执行退货单 ${row.returnNo} 吗？将扣减库存并生成 WMS 出库单。`"
            confirm-button-text="执行"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="360"
            @confirm="execute(row.id)"
          >
            <template #reference>
              <el-button link type="danger" :loading="executingId === row.id">执行</el-button>
            </template>
          </el-popconfirm>

          <el-popconfirm
            v-if="canCancel && (row.status === 1 || row.status === 2)"
            :title="`确认作废退货单 ${row.returnNo} 吗？`"
            confirm-button-text="作废"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="320"
            @confirm="cancel(row.id)"
          >
            <template #reference>
              <el-button link type="danger" :loading="cancelingId === row.id">作废</el-button>
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

  <el-dialog v-model="createVisible" title="新建退货单" width="1000px">
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="供应商">
        <el-select v-model="createForm.supplierId" clearable filterable placeholder="请选择供应商" style="width: 360px">
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.partnerName} (${s.partnerCode})`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="createForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 360px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="createForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 8px 0">
        <div style="font-weight: 600">明细</div>
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
              <el-option
                v-for="p in productOptions"
                :key="p.id"
                :label="`${p.productName} (${p.productCode})`"
                :value="p.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.price" :min="0" :precision="2" :step="1" controls-position="right" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :precision="3" :step="1" controls-position="right" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">
            {{ formatAmount((Number(row.price) || 0) * (Number(row.qty) || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 10px; font-weight: 600">
        合计：{{ formatAmount(totalAmount) }}
      </div>
    </el-form>

    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="退货单详情" width="1000px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="单号">{{ detail.header.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.header.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.header.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.header.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ detail.header.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.header.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="detail.header.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="detail.header.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.header.wmsBillNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.header.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.header.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.header.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.header.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.header.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.header.executeBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detail.header.executeTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="price" label="单价" width="120" />
        <el-table-column prop="qty" label="数量" width="120" />
        <el-table-column prop="amount" label="金额" width="120" />
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { http } from '../api/http'
import {
  auditPurchaseReturn,
  cancelPurchaseReturn,
  createPurchaseReturn,
  executePurchaseReturn,
  getPurchaseReturn,
  listPurchaseReturns,
  type PurReturn,
  type PurReturnDetail
} from '../api/purchase'
import { listProductOptions, listWarehouseOptions, type ProductOption, type WarehouseOption } from '../api/wms'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('pur:return:add'))
const canAudit = computed(() => auth.hasPerm('pur:return:audit'))
const canExecute = computed(() => auth.hasPerm('pur:return:execute'))
const canCancel = computed(() => auth.hasPerm('pur:return:cancel'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<PurReturn[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const suppliers = ref<PartnerOption[]>([])
const warehouses = ref<WarehouseOption[]>([])

const createVisible = ref(false)
const saving = ref(false)
const createForm = ref<{
  supplierId?: number
  warehouseId?: number
  returnDate: string
  remark?: string
  lines: { productId?: number; price: number; qty: number }[]
}>({
  supplierId: undefined,
  warehouseId: undefined,
  returnDate: new Date().toISOString().slice(0, 10),
  remark: '',
  lines: [{ productId: undefined, price: 0, qty: 1 }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurReturnDetail | null>(null)

const auditingId = ref<number | null>(null)
const executingId = ref<number | null>(null)
const cancelingId = ref<number | null>(null)

const totalAmount = computed(() => {
  return createForm.value.lines.reduce((sum, l) => sum + (Number(l.price) || 0) * (Number(l.qty) || 0), 0)
})

function formatAmount(v: number) {
  if (!Number.isFinite(v)) return '0.00'
  return v.toFixed(2)
}

async function reload() {
  loading.value = true
  try {
    const res = await listPurchaseReturns({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载退货单失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  createForm.value = {
    supplierId: undefined,
    warehouseId: undefined,
    returnDate: new Date().toISOString().slice(0, 10),
    remark: '',
    lines: [{ productId: undefined, price: 0, qty: 1 }]
  }
  createVisible.value = true
  searchProducts('')
}

function addLine() {
  createForm.value.lines.push({ productId: undefined, price: 0, qty: 1 })
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
  if (!createForm.value.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!createForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  const lines = createForm.value.lines
    .filter((l) => l.productId != null && Number(l.qty) > 0)
    .map((l) => ({ productId: l.productId as number, price: Number(l.price) || 0, qty: Number(l.qty) }))
  if (!lines.length) {
    ElMessage.warning('请至少选择 1 个商品并填写数量')
    return
  }

  saving.value = true
  try {
    await createPurchaseReturn({
      supplierId: createForm.value.supplierId,
      warehouseId: createForm.value.warehouseId,
      returnDate: createForm.value.returnDate,
      remark: createForm.value.remark || undefined,
      lines
    })
    ElMessage.success('已创建')
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPurchaseReturn(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function audit(id: number) {
  auditingId.value = id
  try {
    await auditPurchaseReturn(id)
    ElMessage.success('已审核')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    auditingId.value = null
  }
}

async function execute(id: number) {
  executingId.value = id
  try {
    const res = await executePurchaseReturn(id)
    ElNotification({ title: '执行成功', message: `已生成 WMS 单：${res.wmsBillNo}`, type: 'success', duration: 3500 })
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function cancel(id: number) {
  cancelingId.value = id
  try {
    await cancelPurchaseReturn(id)
    ElMessage.success('已作废')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  } finally {
    cancelingId.value = null
  }
}

async function loadSuppliers() {
  const res = await http.get<PartnerOption[]>('/api/base/partners/options', { params: { limit: 500 } })
  suppliers.value = (res.data || []).filter((p) => p.type === 1)
}

onMounted(async () => {
  await Promise.all([
    reload(),
    loadSuppliers().catch(() => {
      suppliers.value = []
    }),
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

