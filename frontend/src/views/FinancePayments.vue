<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">收付款流水</div>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; justify-content: flex-end">
          <el-button v-if="canManual" type="primary" @click="openManual">手工收付款</el-button>
          <el-button v-if="canTransferAdd" type="warning" @click="openTransfer">资金调拨</el-button>
          <el-select v-model="type" clearable placeholder="类型" style="width: 120px">
            <el-option :value="1" label="收款" />
            <el-option :value="2" label="付款" />
          </el-select>
          <el-select v-model="accountId" clearable filterable placeholder="账户" style="width: 220px">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 260px"
            unlink-panels
          />
          <el-input v-model="keyword" placeholder="搜索 流水号/单据号/往来单位/账户" style="width: 260px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="payNo" label="流水号" width="210" />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.type === 1" type="success">收款</el-tag>
          <el-tag v-else type="warning">付款</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizType" label="来源" width="120">
        <template #default="{ row }">
          {{ bizTypeLabel(row.bizType) }}
        </template>
      </el-table-column>
      <el-table-column prop="partnerName" label="往来单位" min-width="180">
        <template #default="{ row }">
          {{ row.partnerName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="accountName" label="账户" min-width="160" />
      <el-table-column prop="amount" label="金额" width="140">
        <template #default="{ row }">
          {{ formatMoney(row.amount) }}
        </template>
      </el-table-column>
      <el-table-column prop="bizNo" label="关联单据" width="200" />
      <el-table-column prop="payDate" label="日期" width="120" />
      <el-table-column prop="method" label="方式" width="140" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">完成</el-tag>
          <el-tag v-else type="info">作废</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="操作人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            v-if="row.status === 1 && canCancelRow(row)"
            title="确认作废该流水吗？"
            confirm-button-text="作废"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="260"
            @confirm="doCancelRow(row)"
          >
            <template #reference>
              <el-button type="danger" link>作废</el-button>
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

  <el-dialog v-model="manualVisible" title="手工收付款" width="720px">
    <el-form :model="manualForm" label-width="110px">
      <el-form-item label="类型">
        <el-radio-group v-model="manualForm.type">
          <el-radio :label="1">收款</el-radio>
          <el-radio :label="2">付款</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="资金账户">
        <el-select v-model="manualForm.accountId" placeholder="默认账户" style="width: 320px">
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="往来单位">
        <el-select v-model="manualForm.partnerId" clearable filterable placeholder="可选" style="width: 320px">
          <el-option v-for="p in partners" :key="p.id" :label="`${p.partnerName} (${p.partnerCode})`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="manualForm.payDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
      </el-form-item>
      <el-form-item label="金额">
        <el-input-number v-model="manualForm.amount" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="方式">
        <el-input v-model="manualForm.method" placeholder="例如：银行转账/现金" />
      </el-form-item>
      <el-form-item label="外部关联号">
        <el-input v-model="manualForm.bizNo" placeholder="可选，例如回单号/凭证号" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="manualForm.remark" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="manualVisible = false">取消</el-button>
      <el-button type="primary" :loading="manualSubmitting" @click="submitManual">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="transferVisible" title="资金调拨" width="680px">
    <el-form :model="transferForm" label-width="110px">
      <el-form-item label="转出账户">
        <el-select v-model="transferForm.fromAccountId" placeholder="必选" style="width: 320px">
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="转入账户">
        <el-select v-model="transferForm.toAccountId" placeholder="必选" style="width: 320px">
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="transferForm.transferDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
      </el-form-item>
      <el-form-item label="金额">
        <el-input-number v-model="transferForm.amount" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="transferForm.remark" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="transferVisible = false">取消</el-button>
      <el-button type="warning" :loading="transferSubmitting" @click="submitTransfer">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  cancelManualPayment,
  cancelTransfer,
  createManualPayment,
  createTransfer,
  listFinanceAccountOptions,
  listFinancePayments,
  type FinAccountOption,
  type FinPayment
} from '../api/finance'
import { http } from '../api/http'
import { useAuthStore } from '../stores/auth'

const keyword = ref('')
const type = ref<number | undefined>(undefined)
const accountId = ref<number | undefined>(undefined)
const dateRange = ref<[string, string] | undefined>(undefined)
const accounts = ref<FinAccountOption[]>([])
const partners = ref<PartnerOption[]>([])

const loading = ref(false)
const rows = ref<FinPayment[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

type PartnerOption = { id: number; partnerCode: string; partnerName: string; type: number }

const auth = useAuthStore()
const canManual = computed(() => auth.hasPerm('fin:payment:manual'))
const canTransferAdd = computed(() => auth.hasPerm('fin:transfer:add'))
const canTransferCancel = computed(() => auth.hasPerm('fin:transfer:cancel'))

function formatMoney(v: any) {
  const n = Number(v || 0)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

function bizTypeLabel(bizType: number) {
  if (bizType === 1) return '销售收款'
  if (bizType === 2) return '采购付款'
  if (bizType === 3 || bizType === 4) return '资金调拨'
  if (bizType === 5 || bizType === 6) return '手工收付款'
  return `未知(${bizType})`
}

async function reload() {
  loading.value = true
  try {
    const startDate = dateRange.value?.[0]
    const endDate = dateRange.value?.[1]
    const res = await listFinancePayments({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      type: type.value,
      accountId: accountId.value,
      startDate: startDate || undefined,
      endDate: endDate || undefined
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载流水失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

const manualVisible = ref(false)
const manualSubmitting = ref(false)
const manualForm = ref<{
  type: number
  accountId?: number
  partnerId?: number
  payDate: string
  amount: number
  method?: string
  bizNo?: string
  remark?: string
}>({
  type: 1,
  accountId: undefined,
  partnerId: undefined,
  payDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  method: '',
  bizNo: '',
  remark: ''
})

function openManual() {
  const defaultAccountId = accounts.value?.[0]?.id
  manualForm.value = {
    type: 1,
    accountId: defaultAccountId,
    partnerId: undefined,
    payDate: new Date().toISOString().slice(0, 10),
    amount: 0,
    method: '',
    bizNo: '',
    remark: ''
  }
  manualVisible.value = true
}

async function submitManual() {
  const amount = Number(manualForm.value.amount || 0)
  if (!(amount > 0)) {
    ElMessage.warning('金额必须大于 0')
    return
  }
  manualSubmitting.value = true
  try {
    await createManualPayment({
      type: manualForm.value.type,
      accountId: manualForm.value.accountId || undefined,
      partnerId: manualForm.value.partnerId || undefined,
      amount,
      payDate: manualForm.value.payDate,
      method: manualForm.value.method || undefined,
      bizNo: manualForm.value.bizNo || undefined,
      remark: manualForm.value.remark || undefined
    })
    ElMessage.success('已登记')
    manualVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '登记失败')
  } finally {
    manualSubmitting.value = false
  }
}

const transferVisible = ref(false)
const transferSubmitting = ref(false)
const transferForm = ref<{ fromAccountId?: number; toAccountId?: number; transferDate: string; amount: number; remark?: string }>({
  fromAccountId: undefined,
  toAccountId: undefined,
  transferDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  remark: ''
})

function openTransfer() {
  transferForm.value = {
    fromAccountId: accounts.value?.[0]?.id,
    toAccountId: accounts.value?.[1]?.id,
    transferDate: new Date().toISOString().slice(0, 10),
    amount: 0,
    remark: ''
  }
  transferVisible.value = true
}

async function submitTransfer() {
  const fromId = transferForm.value.fromAccountId
  const toId = transferForm.value.toAccountId
  if (!fromId || !toId) {
    ElMessage.warning('请选择转出/转入账户')
    return
  }
  if (fromId === toId) {
    ElMessage.warning('转出/转入账户不能相同')
    return
  }
  const amount = Number(transferForm.value.amount || 0)
  if (!(amount > 0)) {
    ElMessage.warning('金额必须大于 0')
    return
  }
  transferSubmitting.value = true
  try {
    await createTransfer({
      fromAccountId: fromId,
      toAccountId: toId,
      amount,
      transferDate: transferForm.value.transferDate,
      remark: transferForm.value.remark || undefined
    })
    ElMessage.success('调拨成功')
    transferVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '调拨失败')
  } finally {
    transferSubmitting.value = false
  }
}

function canCancelRow(row: FinPayment) {
  const bizType = row.bizType
  if (bizType === 5 || bizType === 6) return canManual.value
  if (bizType === 3 || bizType === 4) return canTransferCancel.value
  return false
}

async function doCancelRow(row: FinPayment) {
  try {
    const bizType = row.bizType
    if ((bizType === 5 || bizType === 6) && canManual.value) {
      await cancelManualPayment(row.bizId)
      ElMessage.success('已作废')
      await reload()
      return
    }
    if ((bizType === 3 || bizType === 4) && canTransferCancel.value) {
      await cancelTransfer(row.bizId)
      ElMessage.success('已作废')
      await reload()
      return
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '作废失败')
  }
}

async function loadPartners() {
  try {
    const res = await http.get<PartnerOption[]>('/api/base/partners/options', { params: { limit: 800 } })
    partners.value = res.data || []
  } catch {
    partners.value = []
  }
}

onMounted(async () => {
  await Promise.all([
    reload(),
    loadPartners(),
    listFinanceAccountOptions({ limit: 200 })
      .then((res) => {
        accounts.value = res
      })
      .catch(() => {
        accounts.value = []
      })
  ])
})
</script>
