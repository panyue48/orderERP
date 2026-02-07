<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购入库单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input
            v-model="keyword"
            placeholder="搜索入库单号/采购单号/供应商/WMS单号"
            style="width: 320px"
            clearable
            @keyup.enter="reload"
          />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="success" @click="openReceive()">新增收货批次</el-button>
          <el-button v-if="canCreate" type="primary" @click="openCreate">新建采购入库</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="inboundNo" label="入库单号" width="210" />
      <el-table-column prop="orderNo" label="采购单号" width="220" />
      <el-table-column prop="supplierName" label="供应商" min-width="160" />
      <el-table-column prop="warehouseName" label="仓库" min-width="140" />
      <el-table-column prop="wmsBillNo" label="WMS单号" width="190" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 8" type="info">已冲销</el-tag>
          <el-tag v-else-if="row.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待质检</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-popconfirm
            v-if="canIqc && row.status === 1"
            :title="`确认质检通过并入库吗？将生成 WMS 入库单并增加库存。`"
            confirm-button-text="通过"
            cancel-button-text="取消"
            confirm-button-type="success"
            width="360"
            @confirm="iqcPass(row.id)"
          >
            <template #reference>
              <el-button link type="success" :loading="iqcPassingId === row.id">质检通过</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm
            v-if="canIqc && row.status === 1"
            :title="`确认质检不合格并作废该入库单吗？`"
            confirm-button-text="不合格"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="320"
            @confirm="iqcReject(row.id)"
          >
           <template #reference>
             <el-button link type="danger" :loading="iqcRejectingId === row.id">不合格</el-button>
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

  <el-dialog v-model="detailVisible" title="采购入库单详情" width="980px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="入库单号">{{ detail.inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ detail.inbound.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.inbound.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.inbound.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.inbound.wmsBillNo }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.inbound.reverseWmsBillNo" label="冲销WMS单号">{{ detail.inbound.reverseWmsBillNo }}</el-descriptions-item>
        <el-descriptions-item label="质检状态">
          <el-tag v-if="detail.inbound.qcStatus === 2" type="success">通过</el-tag>
          <el-tag v-else-if="detail.inbound.qcStatus === 3" type="danger">不合格</el-tag>
          <el-tag v-else type="warning">待质检</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="质检人">{{ detail.inbound.qcBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="质检时间">{{ detail.inbound.qcTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="质检备注">{{ detail.inbound.qcRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.inbound.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.inbound.createTime }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.inbound.executeBy }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detail.inbound.executeTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.inbound.remark }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin: 12px 0; font-weight: 600">明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="220" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="planQty" label="计划数量" width="130" />
        <el-table-column prop="realQty" label="实际数量" width="130" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-popconfirm
        v-if="canReverse && detail?.inbound?.status === 2 && !detail?.inbound?.reverseWmsBillNo"
        :title="`确认冲销入库单 ${detail?.inbound?.inboundNo} 吗？将生成一张 WMS 出库单并回滚库存与入库进度。`"
        confirm-button-text="冲销"
        cancel-button-text="取消"
        confirm-button-type="danger"
        width="420"
        @confirm="reverse(detail!.inbound.id)"
      >
        <template #reference>
          <el-button type="danger" :loading="reversingId === detail?.inbound?.id">冲销</el-button>
        </template>
      </el-popconfirm>
    </template>
  </el-dialog>

  <el-dialog v-model="createVisible" title="新建采购入库（自动生成采购订单）" width="1100px">
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
        <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 8px 0">
        <div style="font-weight: 600">明细（可设置本次入库数量，支持分批入库）</div>
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
        <el-table-column label="单价" width="150">
          <template #default="{ row }">
            <el-input-number
              v-if="canPriceView"
              v-model="row.price"
              :min="0"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 130px"
              :disabled="!canPriceEdit"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="采购数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :precision="3" :step="1" controls-position="right" style="width: 130px" />
          </template>
        </el-table-column>
        <el-table-column label="本次入库" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.inboundQty"
              :min="0"
              :max="Number(row.qty) || 0"
              :precision="3"
              :step="1"
              controls-position="right"
              style="width: 130px"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">
            {{ canPriceView ? formatAmount((Number(row.price) || 0) * (Number(row.qty) || 0)) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 10px; font-weight: 600">
        合计：{{ canPriceView ? formatAmount(totalAmount) : '-' }}
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">保存并入库</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="receiveVisible" title="新增收货批次（待质检）" width="980px">
    <el-form :model="receiveForm" label-width="90px">
      <el-form-item label="采购单">
        <el-select
          v-model="receiveForm.orderId"
          filterable
          remote
          clearable
          placeholder="搜索采购单号/供应商"
          style="width: 520px"
          :remote-method="searchOrders"
          :loading="orderLoading"
          @change="onReceiveOrderChange"
        >
          <el-option v-for="o in orderOptions" :key="o.id" :label="`${o.orderNo} / ${o.supplierName || '-'}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="receiveForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 360px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="receiveForm.remark" placeholder="可选" />
      </el-form-item>

      <div v-if="receiveLoading" style="padding: 12px 0">加载采购单明细中...</div>
      <div v-else>
        <el-table :data="receiveLines" border size="small" style="width: 100%">
          <el-table-column prop="productCode" label="SKU" width="160" />
          <el-table-column prop="productName" label="商品名称" min-width="220" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="orderedQty" label="采购数量" width="120" />
          <el-table-column prop="inQty" label="已入库" width="120" />
          <el-table-column prop="remainingQty" label="剩余可入" width="120" />
          <el-table-column label="本次到货" width="160">
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
      <el-button @click="receiveVisible = false">取消</el-button>
      <el-button type="success" :loading="receiving" :disabled="receiveLoading" @click="submitReceive">生成待检批次</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { useRoute } from 'vue-router'
import { http } from '../api/http'
import {
  batchInboundPurchaseOrder,
  createPurchaseInbound,
  getPurchaseInbound,
  getPurchaseOrder,
  getPurchaseOrderPendingQcSummary,
  iqcPassPurchaseInbound,
  iqcRejectPurchaseInbound,
  listPurchaseInbounds,
  listPurchaseOrderOptions,
  reversePurchaseInbound,
  type PurOrderOption,
  type PurPendingQcSummary,
  type PurInbound,
  type PurInboundDetail
} from '../api/purchase'
import { listProductOptions, listWarehouseOptions, type ProductOption, type WarehouseOption } from '../api/wms'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

const auth = useAuthStore()
const canView = computed(() => auth.hasPerm('pur:inbound:view'))
const canAdd = computed(() => auth.hasPerm('pur:inbound:add'))
const canIqc = computed(() => auth.hasPerm('pur:inbound:iqc'))
const canReverse = computed(() => auth.hasPerm('pur:inbound:reverse'))
const canPriceView = computed(() => auth.hasPerm('pur:price:view') || auth.hasPerm('pur:price:edit'))
const canPriceEdit = computed(() => auth.hasPerm('pur:price:edit'))
const canCreate = computed(() => canAdd.value && canPriceEdit.value)

const keyword = ref('')
const loading = ref(false)
const rows = ref<PurInbound[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const route = useRoute()
const routeOrderId = computed(() => {
  const v = route.query.orderId
  if (v == null || v === '') return undefined
  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
})

const suppliers = ref<PartnerOption[]>([])
const warehouses = ref<WarehouseOption[]>([])

const createVisible = ref(false)
const saving = ref(false)
const createForm = ref<{
  requestNo?: string
  supplierId?: number
  warehouseId?: number
  orderDate: string
  remark?: string
  lines: { productId?: number; price: number; qty: number; inboundQty: number }[]
}>({
  requestNo: undefined,
  supplierId: undefined,
  warehouseId: undefined,
  orderDate: new Date().toISOString().slice(0, 10),
  remark: '',
  lines: [{ productId: undefined, price: 0, qty: 1, inboundQty: 1 }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurInboundDetail | null>(null)

const iqcPassingId = ref<number | null>(null)
const iqcRejectingId = ref<number | null>(null)
const reversingId = ref<number | null>(null)

const receiveVisible = ref(false)
const receiveLoading = ref(false)
const receiving = ref(false)
const orderLoading = ref(false)
const orderOptions = ref<PurOrderOption[]>([])
const receiveForm = ref<{ requestNo?: string; orderId?: number; warehouseId?: number; remark?: string }>({
  requestNo: undefined,
  orderId: undefined,
  warehouseId: undefined,
  remark: ''
})
const receiveLines = ref<
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
  if (!canView.value) return
  loading.value = true
  try {
    const res = await listPurchaseInbounds({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      orderId: routeOrderId.value
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载入库单失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPurchaseInbound(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function newRequestNo() {
  const c: any = (globalThis as any).crypto
  if (c?.randomUUID) return c.randomUUID()
  return `REQ-${Date.now()}-${Math.floor(Math.random() * 1e9)}`
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

async function loadSuppliers() {
  const res = await http.get<PartnerOption[]>('/api/base/partners/options', { params: { limit: 500 } })
  suppliers.value = (res.data || []).filter((p) => p.type === 1)
}

function openCreate() {
  createForm.value = {
    requestNo: newRequestNo(),
    supplierId: undefined,
    warehouseId: undefined,
    orderDate: new Date().toISOString().slice(0, 10),
    remark: '',
    lines: [{ productId: undefined, price: 0, qty: 1, inboundQty: 1 }]
  }
  createVisible.value = true
  searchProducts('')
}

function addLine() {
  createForm.value.lines.push({ productId: undefined, price: 0, qty: 1, inboundQty: 1 })
}

function removeLine(idx: number) {
  createForm.value.lines.splice(idx, 1)
}

async function submitCreate() {
  if (!canPriceEdit.value) {
    ElMessage.error('无权限编辑单价，无法新建采购入库（自动生成采购订单）')
    return
  }
  if (!createForm.value.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!createForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  if (!createForm.value.requestNo) {
    createForm.value.requestNo = newRequestNo()
  }

  const lines = createForm.value.lines
    .filter((l) => l.productId != null && Number(l.qty) > 0)
    .map((l) => ({
      productId: l.productId as number,
      price: Number(l.price) || 0,
      qty: Number(l.qty),
      inboundQty: Number(l.inboundQty) || 0
    }))
  if (!lines.length) {
    ElMessage.warning('请至少选择 1 个商品并填写采购数量')
    return
  }
  if (!lines.some((l) => l.inboundQty > 0)) {
    ElMessage.warning('请至少填写 1 行本次入库数量')
    return
  }
  if (lines.some((l) => l.inboundQty < 0 || l.inboundQty > l.qty)) {
    ElMessage.warning('本次入库数量需满足 0 <= 入库 <= 采购数量')
    return
  }

  saving.value = true
  try {
    const res = await createPurchaseInbound({
      requestNo: createForm.value.requestNo,
      supplierId: createForm.value.supplierId,
      orderDate: createForm.value.orderDate,
      warehouseId: createForm.value.warehouseId,
      remark: createForm.value.remark || undefined,
      lines
    })
    ElNotification({ title: '已创建入库单', message: `入库单：${res.inboundNo}（待质检）`, type: 'success', duration: 3500 })
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建入库失败')
  } finally {
    saving.value = false
  }
}

async function searchOrders(query: string) {
  orderLoading.value = true
  try {
    orderOptions.value = await listPurchaseOrderOptions({ keyword: query || undefined, limit: 200 })
  } catch {
    orderOptions.value = []
  } finally {
    orderLoading.value = false
  }
}

async function loadReceiveOrderDetail(orderId: number) {
  receiveLoading.value = true
  try {
    const d = await getPurchaseOrder(orderId)
    if (d.status !== 2 && d.status !== 3) {
      ElMessage.warning('当前采购单状态不允许新增收货批次')
      receiveLines.value = []
      return
    }
    receiveLines.value = (d.items || [])
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
    receiveLines.value = []
  } finally {
    receiveLoading.value = false
  }
}

async function onReceiveOrderChange() {
  const orderId = receiveForm.value.orderId
  receiveLines.value = []
  if (!orderId) return
  await loadReceiveOrderDetail(orderId)
}

async function openReceive(orderId?: number) {
  receiveForm.value = { requestNo: newRequestNo(), orderId: orderId ?? routeOrderId.value, warehouseId: undefined, remark: '' }
  receiveLines.value = []
  receiveVisible.value = true
  await searchOrders('')
  if (receiveForm.value.orderId) {
    await loadReceiveOrderDetail(receiveForm.value.orderId)
  }
}

function fmtQty(v: any) {
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v ?? 0)
  return n.toFixed(3).replace(/\\.0+$/, '').replace(/(\\.\\d*?)0+$/, '$1')
}

async function confirmIfPendingQc(orderId: number) {
  let summary: PurPendingQcSummary | null = null
  try {
    summary = await getPurchaseOrderPendingQcSummary(orderId)
  } catch {
    return
  }
  const pendingCount = Number(summary?.pendingCount || 0)
  if (!pendingCount) return

  const pendingQty = fmtQty(summary?.pendingQty)
  const items = (summary?.items || [])
    .slice(0, 12)
    .map((it) => `${it.productName || it.productCode || it.productId}: ${fmtQty(it.qty)}`)
    .join('<br/>')

  const msg = `当前已有 <b>${pendingCount}</b> 张待质检批次单，待检数量合计 <b>${pendingQty}</b>。<br/>` +
    `库存未增加是因为仍处于“待质检库存”，仅在“质检通过”后转入可用库存。<br/>` +
    (items ? `<br/>待检数量汇总：<br/>${items}` : '')

  await ElMessageBox.confirm(msg, '提示', {
    type: 'warning',
    confirmButtonText: '继续新增到货批次',
    cancelButtonText: '取消',
    dangerouslyUseHTMLString: true
  })
}

async function submitReceive() {
  const orderId = receiveForm.value.orderId
  if (!orderId) {
    ElMessage.warning('请选择采购单')
    return
  }
  if (!receiveForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  if (!receiveForm.value.requestNo) {
    receiveForm.value.requestNo = newRequestNo()
  }
  const lines = receiveLines.value
    .filter((l) => Number(l.qty) > 0)
    .map((l) => ({ productId: l.productId, qty: Number(l.qty) }))
  if (!lines.length) {
    ElMessage.warning('请至少填写 1 行本次到货数量')
    return
  }

  try {
    await confirmIfPendingQc(orderId)
  } catch {
    return
  }

  receiving.value = true
  try {
    const res = await batchInboundPurchaseOrder(orderId, {
      requestNo: receiveForm.value.requestNo,
      warehouseId: receiveForm.value.warehouseId,
      remark: receiveForm.value.remark || undefined,
      lines
    })
    ElNotification({ title: '已创建待检批次', message: `入库单：${res.inboundNo}（待质检）`, type: 'success', duration: 3500 })
    receiveVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成批次失败')
  } finally {
    receiving.value = false
  }
}

async function iqcPass(id: number) {
  iqcPassingId.value = id
  try {
    const res = await iqcPassPurchaseInbound(id)
    ElNotification({
      title: '质检通过',
      message: `已入库，WMS单：${res.wmsBillNo || '-'}`,
      type: 'success',
      duration: 3500
    })
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '质检通过失败')
  } finally {
    iqcPassingId.value = null
  }
}

async function iqcReject(id: number) {
  iqcRejectingId.value = id
  try {
    await iqcRejectPurchaseInbound(id)
    ElMessage.success('已标记不合格并作废')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    iqcRejectingId.value = null
  }
}

async function reverse(id: number) {
  reversingId.value = id
  try {
    const res = await reversePurchaseInbound(id)
    ElNotification({
      title: '已冲销',
      message: `冲销WMS单：${res.reversalWmsBillNo || '-'}`,
      type: 'success',
      duration: 3500
    })
    detailVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '冲销失败')
  } finally {
    reversingId.value = null
  }
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

  if (routeOrderId.value && route.query.openReceive === '1') {
    await openReceive(routeOrderId.value)
  }
})
</script>
