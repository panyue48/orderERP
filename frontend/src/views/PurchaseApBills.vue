<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购对账单</div>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; justify-content: flex-end">
          <el-select v-model="supplierId" clearable filterable placeholder="供应商" style="width: 220px">
            <el-option v-for="s in suppliers" :key="s.id" :label="`${s.partnerName} (${s.partnerCode})`" :value="s.id" />
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
          <el-input v-model="keyword" placeholder="搜索对账单号/供应商" style="width: 260px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新建对账单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="billNo" label="对账单号" width="220" />
      <el-table-column prop="supplierName" label="供应商" min-width="180" />
      <el-table-column label="对账周期" width="240">
        <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="对账金额" width="120" />
      <el-table-column prop="paidAmount" label="已付" width="120" />
      <el-table-column prop="invoiceAmount" label="已开票" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="info">草稿</el-tag>
          <el-tag v-else-if="row.status === 2" type="warning">已审核</el-tag>
          <el-tag v-else-if="row.status === 3" type="warning">部分已付</el-tag>
          <el-tag v-else-if="row.status === 4" type="success">已结清</el-tag>
          <el-tag v-else type="danger">已作废</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-popconfirm
            v-if="canCancel && row.status !== 9"
            title="确认作废该对账单吗？（必须未发生付款/开票）"
            confirm-button-text="作废"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="340"
            @confirm="doCancel(row.id)"
          >
            <template #reference>
              <el-button link type="danger">作废</el-button>
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

  <el-dialog v-model="createVisible" title="新建对账单" width="780px">
    <el-form :model="createForm" label-width="110px">
      <el-form-item label="供应商">
        <el-select v-model="createForm.supplierId" clearable filterable placeholder="请选择供应商" style="width: 420px">
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.partnerName} (${s.partnerCode})`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="对账周期">
        <el-date-picker
          v-model="createForm.range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 420px"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>
      <el-alert type="info" show-icon :closable="false" title="说明">
        <template #default>
          <div>对账单会自动汇总该供应商在周期内“已完成”的入库（质检通过）与退货单据。</div>
          <div>已被其他对账单占用的单据不会再次进入本对账单。</div>
        </template>
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="对账单详情" width="1080px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="对账单号">{{ detail.bill.billNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.bill.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="对账周期">{{ detail.bill.startDate }} ~ {{ detail.bill.endDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.bill.status === 1" type="info">草稿</el-tag>
          <el-tag v-else-if="detail.bill.status === 2" type="warning">已审核</el-tag>
          <el-tag v-else-if="detail.bill.status === 3" type="warning">部分已付</el-tag>
          <el-tag v-else-if="detail.bill.status === 4" type="success">已结清</el-tag>
          <el-tag v-else type="danger">已作废</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="对账金额">{{ detail.bill.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="已付/未付">
          {{ detail.bill.paidAmount }} / {{ formatMoney(outstandingAmount) }}
        </el-descriptions-item>
        <el-descriptions-item label="已开票">{{ detail.bill.invoiceAmount }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.bill.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.bill.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.bill.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.bill.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.bill.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 10px 0">
        <div style="font-weight: 600">对账单据</div>
      </div>
      <el-table :data="detail.docs" border size="small" style="width: 100%; margin-bottom: 14px">
        <el-table-column prop="docType" label="类型" width="90">
          <template #default="{ row }">{{ row.docType === 1 ? '入库' : '退货' }}</template>
        </el-table-column>
        <el-table-column prop="docNo" label="单据号" width="220" />
        <el-table-column prop="orderNo" label="采购单号" width="220" />
        <el-table-column prop="productSummary" label="商品" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productSummary || '-' }}</template>
        </el-table-column>
        <el-table-column prop="docTime" label="执行时间" width="180" />
        <el-table-column prop="amount" label="金额" width="140" />
      </el-table>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 10px 0">
        <div style="font-weight: 600">付款记录</div>
        <el-button v-if="canPay && canOpenPay" type="success" @click="openPay">登记付款</el-button>
      </div>
      <el-table :data="detail.payments" border size="small" style="width: 100%; margin-bottom: 14px">
        <el-table-column prop="payNo" label="付款单号" width="220" />
        <el-table-column prop="payDate" label="付款日期" width="120" />
        <el-table-column prop="amount" label="金额" width="140" />
        <el-table-column prop="method" label="方式" width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column prop="createBy" label="创建人" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success">有效</el-tag>
            <el-tag v-else type="danger">作废</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canPay" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.status === 2"
              title="确认作废该付款记录吗？"
              confirm-button-text="作废"
              cancel-button-text="取消"
              confirm-button-type="danger"
              width="280"
              @confirm="cancelPayment(row.id)"
            >
              <template #reference>
                <el-button link type="danger">作废</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; align-items: center; justify-content: space-between; margin: 10px 0">
        <div style="font-weight: 600">发票记录</div>
        <el-button v-if="canInvoice && canOpenInvoice" type="primary" @click="openInvoice">登记发票</el-button>
      </div>
      <el-table :data="detail.invoices" border size="small" style="width: 100%">
        <el-table-column prop="invoiceNo" label="发票号" width="220" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column prop="amount" label="价税合计" width="140" />
        <el-table-column prop="taxAmount" label="税额" width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column prop="createBy" label="创建人" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success">有效</el-tag>
            <el-tag v-else type="danger">作废</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canInvoice" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.status === 2"
              title="确认作废该发票记录吗？"
              confirm-button-text="作废"
              cancel-button-text="取消"
              confirm-button-type="danger"
              width="280"
              @confirm="cancelInvoice(row.id)"
            >
              <template #reference>
                <el-button link type="danger">作废</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-popconfirm
        v-if="canRegen && canOpenRegenerate"
        title="确认重新生成对账单据吗？仅草稿允许，将刷新周期内单据汇总。"
        confirm-button-text="重新生成"
        cancel-button-text="取消"
        confirm-button-type="warning"
        width="360"
        @confirm="doRegenerate(detail!.bill.id)"
      >
        <template #reference>
          <el-button type="warning" :loading="regenerating">重新生成</el-button>
        </template>
      </el-popconfirm>
      <el-button v-if="canAudit && detail?.bill.status === 1" type="warning" :loading="auditing" @click="doAudit(detail!.bill.id)">
        审核
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="payVisible" title="登记付款" width="620px">
    <el-form :model="payForm" label-width="110px">
      <el-form-item label="付款日期">
        <el-date-picker v-model="payForm.payDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
      </el-form-item>
      <el-form-item v-if="accounts.length > 0" label="付款账户">
        <el-select v-model="payForm.accountId" placeholder="默认账户" style="width: 220px">
          <el-option
            v-for="a in accounts"
            :key="a.id"
            :label="`${a.accountName}（余额：${formatMoney(Number(a.balance || 0))}）`"
            :value="a.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="付款金额">
        <el-input-number v-model="payForm.amount" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="付款方式">
        <el-input v-model="payForm.method" placeholder="例如：银行转账/现金" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="payForm.remark" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="payVisible = false">取消</el-button>
      <el-button type="success" :loading="paying" @click="submitPay">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="invoiceVisible" title="登记发票" width="660px">
    <el-form :model="invoiceForm" label-width="110px">
      <el-form-item label="发票号">
        <el-input v-model="invoiceForm.invoiceNo" placeholder="必填" />
      </el-form-item>
      <el-form-item label="开票日期">
        <el-date-picker v-model="invoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
      </el-form-item>
      <el-form-item label="价税合计">
        <el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="税额">
        <el-input-number v-model="invoiceForm.taxAmount" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="invoiceForm.remark" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="invoiceVisible = false">取消</el-button>
      <el-button type="primary" :loading="invoicing" @click="submitInvoice">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { http } from '../api/http'
import {
  addPurchaseApInvoice,
  addPurchaseApPayment,
  auditPurchaseApBill,
  cancelPurchaseApBill,
  cancelPurchaseApInvoice,
  cancelPurchaseApPayment,
  createPurchaseApBill,
  getPurchaseApBill,
  listPurchaseApBills,
  regeneratePurchaseApBill,
  type PurApBill,
  type PurApBillDetail
} from '../api/purchase'
import { listFinanceAccountOptions, type FinAccountOption } from '../api/finance'
import { useAuthStore } from '../stores/auth'

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

const auth = useAuthStore()
const canView = computed(() => auth.hasPerm('pur:ap:view'))
const canAdd = computed(() => auth.hasPerm('pur:ap:add'))
const canAudit = computed(() => auth.hasPerm('pur:ap:audit'))
const canRegen = computed(() => auth.hasPerm('pur:ap:regen'))
const canPay = computed(() => auth.hasPerm('pur:ap:pay'))
const canInvoice = computed(() => auth.hasPerm('pur:ap:invoice'))
const canCancel = computed(() => auth.hasPerm('pur:ap:cancel'))

const keyword = ref('')
const supplierId = ref<number | undefined>(undefined)
const dateRange = ref<[string, string] | undefined>(undefined)
const suppliers = ref<PartnerOption[]>([])

const loading = ref(false)
const rows = ref<PurApBill[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref<{ supplierId?: number; range?: [string, string]; remark?: string }>({
  supplierId: undefined,
  range: undefined,
  remark: ''
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurApBillDetail | null>(null)
const auditing = ref(false)
const regenerating = ref(false)

const payVisible = ref(false)
const paying = ref(false)
const payForm = ref<{ payDate: string; accountId?: number; amount: number; method?: string; remark?: string }>({
  payDate: new Date().toISOString().slice(0, 10),
  accountId: undefined,
  amount: 0,
  method: '',
  remark: ''
})

const accounts = ref<FinAccountOption[]>([])

const invoiceVisible = ref(false)
const invoicing = ref(false)
const invoiceForm = ref<{ invoiceNo: string; invoiceDate: string; amount: number; taxAmount: number; remark?: string }>({
  invoiceNo: '',
  invoiceDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  taxAmount: 0,
  remark: ''
})

const outstandingAmount = computed(() => {
  const t = Number(detail.value?.bill?.totalAmount || 0)
  const p = Number(detail.value?.bill?.paidAmount || 0)
  return Math.max(0, t - p)
})

const canOpenPay = computed(() => {
  const b = detail.value?.bill
  if (!b) return false
  const totalAmount = Number(b.totalAmount || 0)
  if (totalAmount <= 0) return false
  return (b.status === 2 || b.status === 3) && outstandingAmount.value > 0
})

const canOpenInvoice = computed(() => {
  const b = detail.value?.bill
  if (!b) return false
  return b.status === 2 || b.status === 3 || b.status === 4
})

const canOpenRegenerate = computed(() => {
  const b = detail.value?.bill
  if (!b) return false
  return b.status === 1
})

function formatMoney(v: number) {
  if (!Number.isFinite(v)) return '0.00'
  return v.toFixed(2)
}

async function loadSuppliers() {
  const res = await http.get<PartnerOption[]>('/api/base/partners/options', { params: { limit: 800 } })
  suppliers.value = (res.data || []).filter((p) => p.type === 1)
}

async function loadAccounts() {
  try {
    accounts.value = await listFinanceAccountOptions({ limit: 50 })
  } catch {
    accounts.value = []
  }
}

async function reload() {
  if (!canView.value) return
  loading.value = true
  try {
    const startDate = dateRange.value?.[0]
    const endDate = dateRange.value?.[1]
    const res = await listPurchaseApBills({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      supplierId: supplierId.value,
      startDate: startDate || undefined,
      endDate: endDate || undefined
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载对账单失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  createForm.value = { supplierId: undefined, range: undefined, remark: '' }
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.value.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  const range = createForm.value.range
  if (!range || !range[0] || !range[1]) {
    ElMessage.warning('请选择对账周期')
    return
  }
  creating.value = true
  try {
    const bill = await createPurchaseApBill({
      supplierId: createForm.value.supplierId,
      startDate: range[0],
      endDate: range[1],
      remark: createForm.value.remark || undefined
    })
    ElNotification({ title: '已创建对账单', message: `对账单号：${bill.billNo}`, type: 'success', duration: 3500 })
    createVisible.value = false
    await reload()
    await openDetail(bill.id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建对账单失败')
  } finally {
    creating.value = false
  }
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPurchaseApBill(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function doAudit(id: number) {
  auditing.value = true
  try {
    await auditPurchaseApBill(id)
    ElMessage.success('已审核')
    await openDetail(id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    auditing.value = false
  }
}

async function doRegenerate(id: number) {
  regenerating.value = true
  try {
    await regeneratePurchaseApBill(id)
    ElMessage.success('已重新生成')
    await openDetail(id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重新生成失败')
  } finally {
    regenerating.value = false
  }
}

async function doCancel(id: number) {
  try {
    await cancelPurchaseApBill(id)
    ElMessage.success('已作废')
    await reload()
    if (detail.value?.bill?.id === id) {
      await openDetail(id)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  }
}

function openPay() {
  const defaultAccountId = accounts.value?.[0]?.id
  payForm.value = {
    payDate: new Date().toISOString().slice(0, 10),
    accountId: defaultAccountId,
    amount: Number(outstandingAmount.value.toFixed(2)),
    method: '',
    remark: ''
  }
  payVisible.value = true
}

async function submitPay() {
  const b = detail.value?.bill
  if (!b) return
  const amount = Number(payForm.value.amount || 0)
  if (!(amount > 0)) {
    ElMessage.warning('付款金额必须大于 0')
    return
  }
  paying.value = true
  try {
    await addPurchaseApPayment(b.id, {
      payDate: payForm.value.payDate,
      amount,
      accountId: payForm.value.accountId || undefined,
      method: payForm.value.method || undefined,
      remark: payForm.value.remark || undefined
    })
    ElMessage.success('付款登记成功')
    payVisible.value = false
    await openDetail(b.id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '付款登记失败')
  } finally {
    paying.value = false
  }
}

async function cancelPayment(paymentId: number) {
  const b = detail.value?.bill
  if (!b) return
  try {
    await cancelPurchaseApPayment(b.id, paymentId)
    ElMessage.success('已作废付款记录')
    await openDetail(b.id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  }
}

function openInvoice() {
  invoiceForm.value = {
    invoiceNo: '',
    invoiceDate: new Date().toISOString().slice(0, 10),
    amount: 0,
    taxAmount: 0,
    remark: ''
  }
  invoiceVisible.value = true
}

async function submitInvoice() {
  const b = detail.value?.bill
  if (!b) return
  if (!invoiceForm.value.invoiceNo) {
    ElMessage.warning('请填写发票号')
    return
  }
  const amount = Number(invoiceForm.value.amount || 0)
  const taxAmount = Number(invoiceForm.value.taxAmount || 0)
  if (amount < 0 || taxAmount < 0) {
    ElMessage.warning('金额/税额不能为负')
    return
  }
  invoicing.value = true
  try {
    await addPurchaseApInvoice(b.id, {
      invoiceNo: invoiceForm.value.invoiceNo,
      invoiceDate: invoiceForm.value.invoiceDate,
      amount,
      taxAmount,
      remark: invoiceForm.value.remark || undefined
    })
    ElMessage.success('发票登记成功')
    invoiceVisible.value = false
    await openDetail(b.id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发票登记失败')
  } finally {
    invoicing.value = false
  }
}

async function cancelInvoice(invoiceId: number) {
  const b = detail.value?.bill
  if (!b) return
  try {
    await cancelPurchaseApInvoice(b.id, invoiceId)
    ElMessage.success('已作废发票记录')
    await openDetail(b.id)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  }
}

onMounted(async () => {
  await Promise.all([
    loadSuppliers().catch(() => {
      suppliers.value = []
    }),
    loadAccounts().catch(() => {
      accounts.value = []
    }),
    reload()
  ])
})
</script>
