<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">销售出库单</div>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; justify-content: flex-end">
          <el-select v-model="customerId" clearable filterable placeholder="客户" style="width: 220px">
            <el-option v-for="c in customers" :key="c.id" :label="`${c.partnerName} (${c.partnerCode})`" :value="c.id" />
          </el-select>
          <el-select v-model="warehouseId" clearable filterable placeholder="仓库" style="width: 220px">
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="`${w.warehouseName} (${w.warehouseCode})`"
              :value="w.id"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 320px"
            clearable
          />
          <el-input v-model="keyword" placeholder="搜索发货单号/订单号/客户/WMS单号" style="width: 280px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canCreateOutbound" type="primary" @click="openCreateOutbound">新建销售出库</el-button>
          <el-button v-if="canShipBatch || canShip" type="success" @click="openShipBatch()">新增发货批次</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="shipNo" label="发货单号" width="240" />
      <el-table-column prop="orderNo" label="销售订单号" width="220" />
      <el-table-column prop="customerName" label="客户" min-width="180" />
      <el-table-column prop="warehouseName" label="仓库" min-width="160" />
      <el-table-column prop="shipTime" label="发货时间" width="180" />
      <el-table-column label="冲销状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.reverseStatus === 1" type="danger">已冲销</el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalQty" label="数量合计" width="120" />
      <el-table-column prop="wmsBillNo" label="WMS单号" width="200" />
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-if="canReverse" link type="danger" :disabled="row.reverseStatus === 1" @click="reverseShip(row)">
            冲销
          </el-button>
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

  <el-dialog v-model="createVisible" title="新建销售出库（生成订单→锁库→发货）" width="980px">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="说明：此处用于快速完成“下单→审核锁库→发货扣库”。成功后会生成销售订单与对应的销售出库单（发货记录）。"
      style="margin-bottom: 12px"
    />
    <el-form :model="createForm" label-width="110px">
      <el-form-item label="客户">
        <el-select v-model="createForm.customerId" clearable filterable placeholder="请选择客户" style="width: 360px">
          <el-option v-for="c in customers" :key="c.id" :label="`${c.partnerName} (${c.partnerCode})`" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="发货仓库">
        <el-select v-model="createForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 360px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单日期">
        <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>
      <el-form-item label="明细">
        <div style="width: 100%">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
            <div style="color: #666">至少 1 行。提交后会尝试自动审核锁库并发货。</div>
            <el-button @click="addLine">新增行</el-button>
          </div>
          <el-table :data="createForm.lines" border size="small" style="width: 100%">
            <el-table-column label="商品" min-width="420">
              <template #default="{ row }">
                <el-select
                  v-model="row.productId"
                  filterable
                  remote
                  placeholder="搜索 SKU / 商品名"
                  style="width: 100%"
                  :remote-method="searchProducts"
                  :loading="productLoading"
                >
                  <el-option v-for="p in productOptions" :key="p.id" :label="`${p.productName} (${p.productCode})`" :value="p.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="0" :precision="3" :step="1" />
              </template>
            </el-table-column>
            <el-table-column label="单价(可选)" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" :step="1" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeLine($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreateOutbound">提交</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="shipVisible" title="新增发货批次（按销售订单）" width="1000px">
    <div v-if="shipLoading" style="padding: 16px">加载中...</div>
    <div v-else>
      <el-form label-width="110px" style="margin-bottom: 8px">
        <el-form-item label="销售订单">
          <el-select
            v-model="shipOrderId"
            clearable
            filterable
            remote
            placeholder="搜索订单号/客户"
            style="width: 520px"
            :remote-method="searchOrders"
            :loading="orderLoading"
            @change="onShipOrderChange"
          >
            <el-option v-for="o in orderOptions" :key="o.id" :label="`${o.orderNo} | ${o.customerName} | ${o.warehouseName}`" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-if="shipOrder">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="说明：本次发货数量会扣减物理库存并释放对应锁库；将生成发货批次与 WMS 销售出库单。"
          style="margin-bottom: 12px"
        />
        <el-table :data="shipLines" border size="small">
          <el-table-column prop="productCode" label="SKU" width="160" />
          <el-table-column prop="productName" label="商品名称" min-width="240" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="qty" label="订单数量" width="120">
            <template #default="{ row }">{{ fmtQty(row.qty) }}</template>
          </el-table-column>
          <el-table-column prop="shippedQty" label="已发" width="120">
            <template #default="{ row }">{{ fmtQty(row.shippedQty) }}</template>
          </el-table-column>
          <el-table-column prop="remain" label="未发" width="120">
            <template #default="{ row }">{{ fmtQty(row.remain) }}</template>
          </el-table-column>
          <el-table-column label="本次发货" width="180">
            <template #default="{ row }">
              <el-input-number v-model="row.shipQty" :min="0" :max="row.remain" :precision="3" :step="1" controls-position="right" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="shipVisible = false">取消</el-button>
      <el-button type="success" :loading="shipping" :disabled="!shipOrderId" @click="submitShipBatch">确认发货</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="销售出库单详情" width="980px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="发货单号">{{ detail.header.shipNo }}</el-descriptions-item>
        <el-descriptions-item label="销售订单号">{{ detail.header.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.header.customerName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.header.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ detail.header.shipTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量合计">{{ detail.header.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.header.wmsBillNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="冲销状态">
          <el-tag v-if="detail.header.reverseStatus === 1" type="danger">已冲销</el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="冲销WMS单号">{{ detail.header.reverseWmsBillNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="冲销人">{{ detail.header.reverseBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="冲销时间">{{ detail.header.reverseTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.header.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detail.header.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="qty" label="本次出库数量" width="140" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { http } from '../api/http'
import { listProductOptions, listWarehouseOptions, type ProductOption, type WarehouseOption } from '../api/wms'
import { getSalesShip, listSalesShips, reverseSalesShip, type SalShip, type SalShipDetail } from '../api/sales-ship'
import { auditSalesOrder, createSalesOrder, getSalesOrder, shipSalesOrder, shipSalesOrderBatch, type SalOrder, type SalOrderDetail } from '../api/sales'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }
type OrderOption = { id: number; orderNo: string; customerName?: string | null; warehouseName?: string | null }

const auth = useAuthStore()
const route = useRoute()

const canCreateOutbound = computed(() => auth.hasPerm('sal:order:add') && auth.hasPerm('sal:order:audit') && auth.hasPerm('sal:order:ship'))
const canShip = computed(() => auth.hasPerm('sal:order:ship'))
const canShipBatch = computed(() => auth.hasPerm('sal:order:ship-batch'))
const canReverse = computed(() => auth.hasPerm('sal:ship:reverse'))

const keyword = ref('')
const customerId = ref<number | undefined>(undefined)
const warehouseId = ref<number | undefined>(undefined)
const dateRange = ref<[string, string] | undefined>(undefined)

const customers = ref<PartnerOption[]>([])
const warehouses = ref<WarehouseOption[]>([])

const loading = ref(false)
const rows = ref<SalShip[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SalShipDetail | null>(null)

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref<{
  customerId?: number
  warehouseId?: number
  orderDate?: string
  remark?: string
  lines: { productId?: number; qty: number; price?: number | null }[]
}>({
  customerId: undefined,
  warehouseId: undefined,
  orderDate: undefined,
  remark: '',
  lines: [{ productId: undefined, qty: 1, price: undefined }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const shipVisible = ref(false)
const shipLoading = ref(false)
const shipping = ref(false)
const shipOrderId = ref<number | undefined>(undefined)
const shipOrder = ref<SalOrderDetail | null>(null)
const shipLines = ref<
  {
    orderDetailId: number
    productId: number
    productCode?: string
    productName?: string
    unit?: string
    qty: number
    shippedQty: number
    remain: number
    shipQty: number
  }[]
>([])

const orderLoading = ref(false)
const orderOptions = ref<OrderOption[]>([])

async function reload() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value || []
    const res = await listSalesShips({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      customerId: customerId.value,
      warehouseId: warehouseId.value,
      startDate: startDate || undefined,
      endDate: endDate || undefined
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载销售出库单失败')
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
    detail.value = await getSalesShip(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function reverseShip(row: SalShip) {
  if (!row?.id) return
  if (row.reverseStatus === 1) return ElMessage.warning('该出库单已冲销')
  try {
    await ElMessageBox.confirm(
      `确认冲销发货批次 ${row.shipNo}？\n\n冲销会：\n- 回滚物理库存\n- 恢复锁库\n- 回滚订单已发数量\n\n仅用于纠正“误操作发货”。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await reverseSalesShip(row.id)
    ElMessage.success('冲销成功')
    if (detailVisible.value && detail.value?.header?.id === row.id) {
      detail.value = await getSalesShip(row.id)
    }
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '冲销失败')
  }
}

function openCreateOutbound() {
  createForm.value = {
    customerId: undefined,
    warehouseId: undefined,
    orderDate: undefined,
    remark: '',
    lines: [{ productId: undefined, qty: 1, price: undefined }]
  }
  createVisible.value = true
}

function addLine() {
  createForm.value.lines.push({ productId: undefined, qty: 1, price: undefined })
}

function removeLine(i: number) {
  createForm.value.lines.splice(i, 1)
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

async function submitCreateOutbound() {
  if (!createForm.value.customerId) return ElMessage.error('请选择客户')
  if (!createForm.value.warehouseId) return ElMessage.error('请选择发货仓库')
  if (!createForm.value.lines || createForm.value.lines.length === 0) return ElMessage.error('请至少填写 1 行明细')

  const lines = createForm.value.lines
    .filter((l) => l && l.productId)
    .map((l) => ({ productId: l.productId!, qty: Number(l.qty || 0), price: l.price == null ? null : Number(l.price) }))

  if (lines.length === 0) return ElMessage.error('请至少选择 1 个商品')
  if (lines.some((l) => !l.qty || l.qty <= 0)) return ElMessage.error('数量必须大于 0')

  creating.value = true
  try {
    const order: SalOrder = await createSalesOrder({
      customerId: createForm.value.customerId!,
      warehouseId: createForm.value.warehouseId!,
      orderDate: createForm.value.orderDate || undefined,
      remark: createForm.value.remark || undefined,
      lines
    })
    await auditSalesOrder(order.id)
    await shipSalesOrder(order.id)
    ElMessage.success('销售出库已生成')
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally {
    creating.value = false
  }
}

async function openShipBatch(orderId?: number) {
  shipOrderId.value = orderId
  if (!canShipBatch.value && orderId && canShip.value) {
    shipping.value = true
    try {
      await shipSalesOrder(orderId)
      ElMessage.success('发货成功')
      await reload()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '发货失败')
    } finally {
      shipping.value = false
    }
    return
  }

  shipVisible.value = true
  shipLoading.value = false
  shipOrder.value = null
  shipLines.value = []
  if (orderId) {
    await onShipOrderChange(orderId)
  }
}

async function searchOrders(query: string) {
  orderLoading.value = true
  try {
    const res = await http.get<any[]>('/api/sales/orders/options', { params: { keyword: query || undefined, limit: 200 } })
    orderOptions.value = (res.data || []).map((o) => ({
      id: o.id,
      orderNo: o.orderNo,
      customerName: o.customerName,
      warehouseName: o.warehouseName
    }))
  } catch {
    orderOptions.value = []
  } finally {
    orderLoading.value = false
  }
}

async function onShipOrderChange(id?: number) {
  if (!id) {
    shipOrder.value = null
    shipLines.value = []
    return
  }
  shipLoading.value = true
  try {
    shipOrder.value = await getSalesOrder(id)
    const lines =
      shipOrder.value.items
        ?.map((it) => {
          const qty = Number(it.qty || 0)
          const shippedQty = Number(it.shippedQty || 0)
          const remain = qty - shippedQty
          return {
            orderDetailId: it.id,
            productId: it.productId,
            productCode: it.productCode || '',
            productName: it.productName || '',
            unit: it.unit || '',
            qty,
            shippedQty,
            remain,
            shipQty: remain > 0 ? remain : 0
          }
        })
        .filter((l) => l.remain > 0) || []
    shipLines.value = lines
    if (lines.length === 0) {
      ElMessage.info('该订单已无可发数量')
      shipOrder.value = null
      shipOrderId.value = undefined
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载订单失败')
    shipOrder.value = null
  } finally {
    shipLoading.value = false
  }
}

async function submitShipBatch() {
  if (!shipOrderId.value) return

  if (!canShipBatch.value) {
    if (!canShip.value) return ElMessage.error('无发货权限')
    shipping.value = true
    try {
      await shipSalesOrder(shipOrderId.value)
      ElMessage.success('发货成功')
      shipVisible.value = false
      await reload()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '发货失败')
    } finally {
      shipping.value = false
    }
    return
  }

  const lines = shipLines.value
    .filter((l) => l.shipQty && l.shipQty > 0)
    .map((l) => ({ orderDetailId: l.orderDetailId, productId: l.productId, qty: Number(l.shipQty) }))
  if (lines.length === 0) return ElMessage.error('请至少填写 1 行发货数量')

  shipping.value = true
  try {
    await shipSalesOrderBatch(shipOrderId.value, { lines })
    ElMessage.success('发货成功')
    shipVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发货失败')
  } finally {
    shipping.value = false
  }
}

function fmtQty(v: number) {
  if (!Number.isFinite(v)) return '-'
  const s = v.toFixed(3)
  return s.replace(/\.?0+$/, '')
}

async function loadCustomers() {
  const res = await http.get<PartnerOption[]>('/api/base/partners/options', { params: { limit: 800 } })
  customers.value = (res.data || []).filter((p) => p.type === 2)
}

async function loadWarehouses() {
  warehouses.value = await listWarehouseOptions({ limit: 500 })
}

onMounted(async () => {
  try {
    await Promise.all([loadCustomers(), loadWarehouses()])
  } catch {
    // ignore
  }
  const openShip = route.query.openShip === '1' || route.query.openShip === 'true'
  const orderId = route.query.orderId ? Number(route.query.orderId) : undefined
  if (openShip && orderId && Number.isFinite(orderId)) {
    await openShipBatch(orderId)
  }
  await reload()
})
</script>
