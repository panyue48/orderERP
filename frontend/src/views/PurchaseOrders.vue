<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购订单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号/供应商" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新建采购单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="orderNo" label="单号" width="220" />
      <el-table-column prop="orderDate" label="日期" width="120" />
      <el-table-column prop="supplierName" label="供应商" min-width="180" />
      <el-table-column prop="totalAmount" label="总额" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 3" type="warning">部分入库</el-tag>
          <el-tag v-else-if="row.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="row.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="制单人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>

          <el-popconfirm
            v-if="canAudit && row.status === 1"
            title="确认审核该采购单吗？审核后才能入库。"
            confirm-button-text="审核"
            cancel-button-text="取消"
            confirm-button-type="primary"
            width="320"
            @confirm="audit(row.id)"
          >
            <template #reference>
              <el-button link type="primary" :loading="auditingId === row.id">审核</el-button>
            </template>
          </el-popconfirm>

          <el-button
            v-if="canInbound && (row.status === 2 || row.status === 3)"
            link
            type="success"
            @click="openInbound(row.id, row.orderNo)"
          >
            入库
          </el-button>

          <el-popconfirm
            v-if="canCancel && (row.status === 1 || row.status === 2)"
            :title="`确认作废采购单 ${row.orderNo} 吗？`"
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

  <el-dialog v-model="createVisible" title="新建采购单" width="1000px">
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="供应商">
        <el-select
          v-model="createForm.supplierId"
          clearable
          filterable
          placeholder="请选择供应商"
          style="width: 360px"
        >
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.partnerName} (${s.partnerCode})`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
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
            <el-input-number v-model="row.price" :min="0" :step="1" :precision="2" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :step="1" :precision="3" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">
            {{ formatAmount((row.price || 0) * (row.qty || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="createForm.lines.length <= 1" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 10px; font-weight: 700">
        合计：{{ formatAmount(totalAmount) }}
      </div>
    </el-form>

    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="采购单详情" width="1000px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="detail.status === 3" type="warning">部分入库</el-tag>
          <el-tag v-else-if="detail.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="detail.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="price" label="单价" width="120" />
        <el-table-column prop="qty" label="采购数量" width="120" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="inQty" label="已入库" width="120" />
      </el-table>
    </div>
  </el-dialog>

  <el-dialog v-model="inboundVisible" title="采购入库" width="980px">
    <el-form :model="inboundForm" label-width="90px">
      <el-form-item label="仓库">
        <el-select v-model="inboundForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 320px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="inboundForm.remark" placeholder="可选" />
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        title="支持分批入库：本次仅入库填写的数量；已入库会累计，采购单会变为“部分入库/已完成”。"
        show-icon
        style="margin-bottom: 12px"
      />

      <div v-if="inboundDetailLoading" style="padding: 12px 0">加载采购单明细中...</div>
      <div v-else>
        <el-table :data="inboundLines" border size="small" style="width: 100%">
          <el-table-column prop="productCode" label="SKU" width="160" />
          <el-table-column prop="productName" label="商品名称" min-width="220" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="orderedQty" label="采购数量" width="120" />
          <el-table-column prop="inQty" label="已入库" width="120" />
          <el-table-column prop="remainingQty" label="剩余可入" width="120" />
          <el-table-column label="本次入库" width="160">
            <template #default="{ row }">
              <el-input-number
                v-model="row.qty"
                :min="0"
                :max="row.remainingQty"
                :precision="3"
                :step="1"
                controls-position="right"
                style="width: 140px"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="inboundVisible = false">取消</el-button>
      <el-button type="success" :loading="inbounding" :disabled="inboundDetailLoading" @click="submitInbound">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { http } from '../api/http'
import {
  auditPurchaseOrder,
  batchInboundPurchaseOrder,
  cancelPurchaseOrder,
  createPurchaseOrder,
  getPurchaseOrder,
  listPurchaseOrders,
  type PurOrder,
  type PurOrderDetail
} from '../api/purchase'
import { listProductOptions, listWarehouseOptions, type ProductOption, type WarehouseOption } from '../api/wms'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('pur:order:add'))
const canAudit = computed(() => auth.hasPerm('pur:order:audit'))
const canInbound = computed(() => auth.hasPerm('pur:order:inbound'))
const canCancel = computed(() => auth.hasPerm('pur:order:cancel'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<PurOrder[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const suppliers = ref<PartnerOption[]>([])
const warehouses = ref<WarehouseOption[]>([])

const createVisible = ref(false)
const saving = ref(false)
const createForm = ref<{
  supplierId?: number
  orderDate: string
  remark?: string
  lines: { productId?: number; price: number; qty: number }[]
}>({
  supplierId: undefined,
  orderDate: new Date().toISOString().slice(0, 10),
  remark: '',
  lines: [{ productId: undefined, price: 0, qty: 1 }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurOrderDetail | null>(null)

const auditingId = ref<number | null>(null)
const cancelingId = ref<number | null>(null)

const inboundVisible = ref(false)
const inbounding = ref(false)
const inboundForm = ref<{ requestNo?: string; orderId?: number; orderNo?: string; warehouseId?: number; remark?: string }>({
  requestNo: undefined,
  orderId: undefined,
  orderNo: undefined,
  warehouseId: undefined,
  remark: ''
})
const inboundDetailLoading = ref(false)
const inboundLines = ref<
  { productId: number; productCode?: string | null; productName?: string | null; unit?: string | null; orderedQty: number; inQty: number; remainingQty: number; qty: number }[]
>([])

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
    const res = await listPurchaseOrders({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载采购单失败')
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
    orderDate: new Date().toISOString().slice(0, 10),
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
  const lines = createForm.value.lines
    .filter((l) => l.productId != null && Number(l.qty) > 0)
    .map((l) => ({ productId: l.productId as number, price: Number(l.price) || 0, qty: Number(l.qty) }))
  if (!lines.length) {
    ElMessage.warning('请至少选择 1 个商品并填写数量')
    return
  }

  saving.value = true
  try {
    await createPurchaseOrder({
      supplierId: createForm.value.supplierId,
      orderDate: createForm.value.orderDate,
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
    detail.value = await getPurchaseOrder(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function audit(id: number) {
  auditingId.value = id
  try {
    await auditPurchaseOrder(id)
    ElMessage.success('已审核')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    auditingId.value = null
  }
}

async function cancel(id: number) {
  cancelingId.value = id
  try {
    await cancelPurchaseOrder(id)
    ElMessage.success('已作废')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  } finally {
    cancelingId.value = null
  }
}

async function openInbound(orderId: number, orderNo: string) {
  inboundForm.value = { requestNo: newRequestNo(), orderId, orderNo, warehouseId: undefined, remark: '' }
  inboundLines.value = []
  inboundVisible.value = true

  inboundDetailLoading.value = true
  try {
    const d = await getPurchaseOrder(orderId)
    inboundLines.value = (d.items || [])
      .map((it) => {
        const orderedQty = Number(it.qty) || 0
        const inQty = Number(it.inQty) || 0
        const remainingQty = Math.max(0, orderedQty - inQty)
        return {
          productId: it.productId,
          productCode: it.productCode,
          productName: it.productName,
          unit: it.unit,
          orderedQty,
          inQty,
          remainingQty,
          qty: remainingQty
        }
      })
      .filter((x) => x.remainingQty > 0)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载采购单明细失败')
    inboundLines.value = []
  } finally {
    inboundDetailLoading.value = false
  }
}

function newRequestNo() {
  const c: any = (globalThis as any).crypto
  if (c?.randomUUID) return c.randomUUID()
  return `REQ-${Date.now()}-${Math.floor(Math.random() * 1e9)}`
}

async function submitInbound() {
  if (!inboundForm.value.orderId) return
  if (!inboundForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  if (!inboundForm.value.requestNo) {
    inboundForm.value.requestNo = newRequestNo()
  }
  const lines = inboundLines.value
    .filter((l) => Number(l.qty) > 0)
    .map((l) => ({ productId: l.productId, qty: Number(l.qty) }))
  if (!lines.length) {
    ElMessage.warning('请至少填写 1 行入库数量')
    return
  }
  inbounding.value = true
  try {
    const res = await batchInboundPurchaseOrder(inboundForm.value.orderId, {
      requestNo: inboundForm.value.requestNo,
      warehouseId: inboundForm.value.warehouseId,
      remark: inboundForm.value.remark || undefined,
      lines
    })
    ElNotification({
      title: '入库成功',
      message: `入库单：${res.inboundNo}，WMS单：${res.wmsBillNo}`,
      type: 'success',
      duration: 3500
    })
    inboundVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '入库失败')
  } finally {
    inbounding.value = false
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
