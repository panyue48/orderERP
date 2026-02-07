<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">销售退货单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号/客户/WMS单号" style="width: 260px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canCreate" type="primary" @click="openCreate">新建退货单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="returnNo" label="单号" width="220" />
      <el-table-column prop="returnDate" label="日期" width="120" />
      <el-table-column prop="customerName" label="客户" min-width="180" />
      <el-table-column prop="warehouseName" label="仓库" min-width="140" />
      <el-table-column prop="shipNo" label="发货批次" width="220" />
      <el-table-column label="总额" width="120">
        <template #default="{ row }">{{ canPriceView ? row.totalAmount ?? '-' : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 3" type="warning">待质检</el-tag>
          <el-tag v-else-if="row.status === 2" type="info">待收货</el-tag>
          <el-tag v-else-if="row.status === 5" type="danger">不合格</el-tag>
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
            v-if="canReceive && row.status === 2"
            :title="`确认收货并入待检吗？库存不会变化，将进入“待质检”。`"
            confirm-button-text="收货"
            cancel-button-text="取消"
            confirm-button-type="primary"
            width="360"
            @confirm="receive(row.id)"
          >
            <template #reference>
              <el-button link type="primary" :loading="receivingId === row.id">收货入待检</el-button>
            </template>
          </el-popconfirm>

          <el-popconfirm
            v-if="canExecute && row.status === 3"
            :title="`确认质检通过并入库吗？将增加库存并生成 WMS 入库单。`"
            confirm-button-text="通过并入库"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="360"
            @confirm="execute(row.id)"
          >
            <template #reference>
              <el-button link type="danger" :loading="executingId === row.id">质检通过</el-button>
            </template>
          </el-popconfirm>

          <el-button
            v-if="canQcReject && row.status === 3"
            link
            type="danger"
            :loading="qcRejectingId === row.id"
            @click="openQcReject(row)"
          >
            不合格
          </el-button>

          <el-popconfirm
            v-if="canCancel && (row.status === 1 || row.status === 2 || row.status === 3)"
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
      <el-form-item label="发货批次">
        <el-select
          v-model="createForm.shipId"
          clearable
          filterable
          remote
          placeholder="搜索 发货单号/销售订单号/客户/WMS单号"
          style="width: 680px"
          :remote-method="searchShips"
          :loading="shipLoading"
          @change="onShipChange"
        >
          <el-option
            v-for="s in shipOptions"
            :key="s.id"
            :label="`${s.shipNo} | ${s.orderNo || '-'} | ${s.customerName || '-'} | ${s.warehouseName || '-'}`"
            :value="s.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="客户">
        <span>{{ sourceShip?.header?.customerName || '-' }}</span>
      </el-form-item>
      <el-form-item label="仓库">
        <span>{{ sourceShip?.header?.warehouseName || '-' }}</span>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="createForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>

      <el-form-item label="明细">
        <div style="width: 100%">
          <el-table :data="returnLines" border size="small">
            <el-table-column prop="productCode" label="SKU" width="160" />
            <el-table-column prop="productName" label="商品名称" min-width="240" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column label="本批次发货" width="140">
              <template #default="{ row }">{{ fmtQty(row.shippedQty) }}</template>
            </el-table-column>
            <el-table-column label="已退" width="120">
              <template #default="{ row }">{{ fmtQty(row.returnedQty) }}</template>
            </el-table-column>
            <el-table-column label="可退" width="120">
              <template #default="{ row }">{{ fmtQty(row.returnableQty) }}</template>
            </el-table-column>
            <el-table-column label="本次退货" width="180">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.qty"
                  :min="0"
                  :max="row.returnableQty"
                  :precision="3"
                  :step="1"
                  controls-position="right"
                />
              </template>
            </el-table-column>
          </el-table>

          <div style="display: flex; justify-content: flex-end; margin-top: 10px; font-weight: 600">
            本次退货数量合计：{{ fmtQty(totalReturnQty) }}
          </div>
        </div>
      </el-form-item>
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
        <el-descriptions-item label="客户">{{ detail.header.customerName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.header.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="发货批次">{{ detail.header.shipNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ detail.header.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.header.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ canPriceView ? detail.header.totalAmount ?? '-' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.header.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="detail.header.status === 3" type="warning">待质检</el-tag>
          <el-tag v-else-if="detail.header.status === 2" type="info">待收货</el-tag>
          <el-tag v-else-if="detail.header.status === 5" type="danger">不合格</el-tag>
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
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ canPriceView ? row.price ?? '-' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="120" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ canPriceView ? row.amount ?? '-' : '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>

  <el-dialog v-model="qcRejectVisible" title="质检不合格" width="520px">
    <el-form :model="qcRejectForm" label-width="100px">
      <el-form-item label="处置方式">
        <el-select v-model="qcRejectForm.disposition" placeholder="请选择" style="width: 280px">
          <el-option label="退回客户" value="RETURN_TO_CUSTOMER" />
          <el-option label="报废" value="SCRAP" />
          <el-option label="返修" value="REPAIR" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="qcRejectForm.remark" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="qcRejectVisible = false">取消</el-button>
      <el-button type="danger" :loading="qcRejectSubmitting" @click="submitQcReject">确认不合格</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  auditSalesReturn,
  cancelSalesReturn,
  createSalesReturn,
  executeSalesReturn,
  getSalesReturn,
  listSalesReturns,
  qcRejectSalesReturn,
  receiveSalesReturn,
  type SalReturn,
  type SalReturnDetail
} from '../api/sales-return'
import { getSalesShip, listSalesShips, type SalShip, type SalShipDetail } from '../api/sales-ship'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canPriceView = computed(() => auth.hasPerm('sal:price:view') || auth.hasPerm('sal:price:edit'))
const canAdd = computed(() => auth.hasPerm('sal:return:add'))
const canAudit = computed(() => auth.hasPerm('sal:return:audit'))
const canReceive = computed(() => auth.hasPerm('sal:return:receive'))
const canExecute = computed(() => auth.hasPerm('sal:return:execute'))
const canQcReject = computed(() => auth.hasPerm('sal:return:qc-reject'))
const canCancel = computed(() => auth.hasPerm('sal:return:cancel'))
const canCreate = computed(() => canAdd.value)

const keyword = ref('')
const loading = ref(false)
const rows = ref<SalReturn[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const createVisible = ref(false)
const saving = ref(false)
const shipLoading = ref(false)
const shipOptions = ref<SalShip[]>([])
const sourceShip = ref<SalShipDetail | null>(null)

type ReturnLine = {
  shipDetailId: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  shippedQty: number
  returnedQty: number
  returnableQty: number
  qty: number
}
const returnLines = ref<ReturnLine[]>([])

const createForm = ref<{
  shipId?: number
  returnDate: string
  remark?: string
}>({
  shipId: undefined,
  returnDate: new Date().toISOString().slice(0, 10),
  remark: ''
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SalReturnDetail | null>(null)

const auditingId = ref<number | null>(null)
const receivingId = ref<number | null>(null)
const executingId = ref<number | null>(null)
const cancelingId = ref<number | null>(null)
const qcRejectingId = ref<number | null>(null)

const qcRejectVisible = ref(false)
const qcRejectSubmitting = ref(false)
const qcRejectTarget = ref<SalReturn | null>(null)
const qcRejectForm = ref<{ disposition: string; remark: string }>({ disposition: 'RETURN_TO_CUSTOMER', remark: '' })

async function reload() {
  loading.value = true
  try {
    const res = await listSalesReturns({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载销售退货单失败')
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
    detail.value = await getSalesReturn(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function openCreate() {
  createVisible.value = true
  createForm.value = {
    shipId: undefined,
    returnDate: new Date().toISOString().slice(0, 10),
    remark: ''
  }
  shipOptions.value = []
  sourceShip.value = null
  returnLines.value = []
}

function num(v: any) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function fmtQty(v: any) {
  const n = num(v)
  if (!Number.isFinite(n)) return '-'
  const s = n.toFixed(3)
  return s.replace(/\.?0+$/, '')
}

const totalReturnQty = computed(() => returnLines.value.reduce((sum, l) => sum + num(l.qty), 0))

async function searchShips(query: string) {
  shipLoading.value = true
  try {
    const res = await listSalesShips({ page: 0, size: 20, keyword: query || undefined })
    shipOptions.value = res.content || []
  } catch {
    shipOptions.value = []
  } finally {
    shipLoading.value = false
  }
}

async function onShipChange() {
  const shipId = createForm.value.shipId
  sourceShip.value = null
  returnLines.value = []
  if (!shipId) return
  try {
    const d = await getSalesShip(shipId)
    sourceShip.value = d
    returnLines.value =
      d?.items?.map((it) => ({
        shipDetailId: it.id,
        productId: it.productId,
        productCode: it.productCode,
        productName: it.productName,
        unit: it.unit,
        shippedQty: num(it.qty),
        returnedQty: num(it.returnedQty),
        returnableQty: num(it.returnableQty),
        qty: 0
      })) || []
  } catch (e: any) {
    sourceShip.value = null
    returnLines.value = []
    ElMessage.error(e?.response?.data?.message || '加载发货批次失败')
  }
}

async function submitCreate() {
  if (!createForm.value.shipId) return ElMessage.error('请选择发货批次')
  if (!sourceShip.value?.header?.customerId) return ElMessage.error('发货批次缺少客户信息')
  if (!sourceShip.value?.header?.warehouseId) return ElMessage.error('发货批次缺少仓库信息')
  const lines = returnLines.value
    .filter((l) => l.shipDetailId && num(l.qty) > 0)
    .map((l) => ({ shipDetailId: l.shipDetailId, qty: num(l.qty) }))
  if (lines.length <= 0) return ElMessage.error('请至少填写 1 行商品')

  saving.value = true
  try {
    const res = await createSalesReturn({
      shipId: createForm.value.shipId,
      customerId: sourceShip.value.header.customerId,
      warehouseId: sourceShip.value.header.warehouseId,
      returnDate: createForm.value.returnDate,
      remark: createForm.value.remark,
      lines
    })
    ElNotification.success({ title: '成功', message: `已创建退货单 ${res.returnNo}` })
    createVisible.value = false
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function audit(id: number) {
  auditingId.value = id
  try {
    await auditSalesReturn(id)
    ElMessage.success('已审核')
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    auditingId.value = null
  }
}

async function receive(id: number) {
  receivingId.value = id
  try {
    await receiveSalesReturn(id)
    ElMessage.success('已收货入待检')
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '收货失败')
  } finally {
    receivingId.value = null
  }
}

async function execute(id: number) {
  executingId.value = id
  try {
    const res = await executeSalesReturn(id)
    ElNotification.success({ title: '执行成功', message: `已生成 WMS 单 ${res.wmsBillNo || '-'}` })
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

function openQcReject(row: SalReturn) {
  qcRejectTarget.value = row
  qcRejectForm.value = { disposition: 'RETURN_TO_CUSTOMER', remark: '' }
  qcRejectVisible.value = true
}

async function submitQcReject() {
  const row = qcRejectTarget.value
  if (!row?.id) return
  if (!qcRejectForm.value.disposition) return ElMessage.error('请选择处置方式')
  qcRejectSubmitting.value = true
  qcRejectingId.value = row.id
  try {
    await qcRejectSalesReturn(row.id, { disposition: qcRejectForm.value.disposition, remark: qcRejectForm.value.remark || undefined })
    ElMessage.success('已标记不合格')
    qcRejectVisible.value = false
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    qcRejectSubmitting.value = false
    qcRejectingId.value = null
  }
}

async function cancel(id: number) {
  cancelingId.value = id
  try {
    await cancelSalesReturn(id)
    ElMessage.success('已作废')
    reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  } finally {
    cancelingId.value = null
  }
}

onMounted(() => {
  reload()
})
</script>
