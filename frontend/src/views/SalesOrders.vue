<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">销售订单（记录）</div>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; justify-content: flex-end">
          <el-select v-model="customerId" clearable filterable placeholder="客户" style="width: 220px">
            <el-option v-for="c in customers" :key="c.id" :label="`${c.partnerName} (${c.partnerCode})`" :value="c.id" />
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
          <el-input v-model="keyword" placeholder="搜索订单号/客户" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="orderNo" label="订单号" width="220" />
      <el-table-column prop="orderDate" label="日期" width="120" />
      <el-table-column prop="customerName" label="客户" min-width="180" />
      <el-table-column prop="warehouseName" label="发货仓库" min-width="160" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }">{{ canPriceView ? row.totalAmount ?? '-' : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="info">草稿</el-tag>
          <el-tag v-else-if="row.status === 2" type="warning">已审核(锁库)</el-tag>
          <el-tag v-else-if="row.status === 3" type="warning">部分发货</el-tag>
          <el-tag v-else-if="row.status === 4" type="success">已发货</el-tag>
          <el-tag v-else type="danger">已作废</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-if="canDeleteDraft && row.status === 1" link type="danger" @click="deleteDraft(row)">删除</el-button>
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

  <el-dialog v-model="detailVisible" title="销售订单详情" width="1000px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="发货仓库">{{ detail.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ canPriceView ? detail.totalAmount ?? '-' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.status === 1" type="info">草稿</el-tag>
          <el-tag v-else-if="detail.status === 2" type="warning">已审核(锁库)</el-tag>
          <el-tag v-else-if="detail.status === 3" type="warning">部分发货</el-tag>
          <el-tag v-else-if="detail.status === 4" type="success">已发货</el-tag>
          <el-tag v-else type="danger">已作废</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.wmsBillNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货人">{{ detail.shipBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ detail.shipTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="作废人">{{ detail.cancelBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="作废时间">{{ detail.cancelTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ canPriceView ? row.price ?? '-' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="120" />
        <el-table-column prop="shippedQty" label="已发" width="120" />
        <el-table-column label="未发" width="120">
          <template #default="{ row }">
            {{ formatQty(Number(row.qty || 0) - Number(row.shippedQty || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ canPriceView ? row.amount ?? '-' : '-' }}</template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 12px">
        <div style="display: flex; align-items: center; justify-content: space-between; margin: 10px 0">
          <div style="font-weight: 600">发货批次</div>
        </div>
        <el-table :data="ships" border size="small" style="width: 100%">
          <el-table-column prop="shipNo" label="发货单号" width="240" />
          <el-table-column prop="shipTime" label="发货时间" width="180" />
          <el-table-column prop="totalQty" label="数量合计" width="120" />
          <el-table-column prop="wmsBillNo" label="WMS单号" width="200" />
          <el-table-column prop="createBy" label="创建人" width="120" />
          <el-table-column prop="createTime" label="创建时间" width="180" />
        </el-table>
      </div>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-button type="success" :disabled="!detail?.id || !(detail.status === 2 || detail.status === 3)" @click="goOutbound(detail!.id)">
        去发货
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
 import { computed, onMounted, ref } from 'vue'
 import { ElMessage, ElMessageBox } from 'element-plus'
 import { useRouter } from 'vue-router'
 import { http } from '../api/http'
 import { listWarehouseOptions, type WarehouseOption } from '../api/wms'
 import {
  deleteSalesOrderDraft,
  getSalesOrder,
  listSalesOrderShips,
  listSalesOrders,
  type SalOrder,
  type SalOrderDetail,
  type SalShip
} from '../api/sales'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

 const auth = useAuthStore()
 const router = useRouter()
 const canPriceView = computed(() => auth.hasPerm('sal:price:view') || auth.hasPerm('sal:price:edit'))
 const canDeleteDraft = computed(() => auth.hasPerm('sal:order:delete'))

const keyword = ref('')
const customerId = ref<number | undefined>(undefined)
const dateRange = ref<[string, string] | undefined>(undefined)
const customers = ref<PartnerOption[]>([])
const warehouses = ref<WarehouseOption[]>([])

const loading = ref(false)
const rows = ref<SalOrder[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SalOrderDetail | null>(null)
const ships = ref<SalShip[]>([])

async function reload() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value || []
    const res = await listSalesOrders({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      customerId: customerId.value,
      startDate: startDate || undefined,
      endDate: endDate || undefined
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载销售订单失败')
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
  ships.value = []
  try {
    detail.value = await getSalesOrder(id)
    ships.value = await listSalesOrderShips(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function deleteDraft(row: SalOrder) {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm(`确认删除草稿订单 ${row.orderNo} 吗？删除后不可恢复。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteSalesOrderDraft(row.id)
    if (detailVisible.value && detail.value?.id === row.id) {
      detailVisible.value = false
      detail.value = null
      ships.value = []
    }
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function formatQty(v: number) {
  if (!Number.isFinite(v)) return '-'
  const s = v.toFixed(3)
  return s.replace(/\.?0+$/, '')
}

function goOutbound(orderId: number) {
  detailVisible.value = false
  router.push({ path: '/sales/outbounds', query: { orderId: String(orderId), openShip: '1' } })
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
  await reload()
})
</script>
