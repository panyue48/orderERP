<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">资金账户</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-button v-if="canAdd" type="primary" @click="openCreate">新增账户</el-button>
          <el-input
            v-model="keyword"
            placeholder="搜索 账户名称/账号"
            style="width: 260px"
            clearable
            @keyup.enter="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="accountName" label="账户名称" min-width="200" />
      <el-table-column prop="accountNo" label="账号" min-width="220" />
      <el-table-column prop="balance" label="余额" width="140">
        <template #default="{ row }">
          {{ formatMoney(row.balance) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="220" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
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

  <el-dialog v-model="createVisible" title="新增资金账户" width="640px">
    <el-form :model="createForm" label-width="110px">
      <el-form-item label="账户名称">
        <el-input v-model="createForm.accountName" placeholder="必填" />
      </el-form-item>
      <el-form-item label="账号">
        <el-input v-model="createForm.accountNo" placeholder="可选（银行卡号/支付宝号等）" />
      </el-form-item>
      <el-form-item label="期初余额">
        <el-input-number v-model="createForm.openingBalance" :min="0" :precision="2" controls-position="right" style="width: 220px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { createFinanceAccount, listFinanceAccounts, type FinAccount } from '../api/finance'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('fin:account:add'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<FinAccount[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref<{ accountName: string; accountNo?: string; openingBalance: number; remark?: string }>({
  accountName: '',
  accountNo: '',
  openingBalance: 0,
  remark: ''
})

function formatMoney(v: any) {
  const n = Number(v || 0)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

async function reload() {
  loading.value = true
  try {
    const res = await listFinanceAccounts({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载资金账户失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  createForm.value = { accountName: '', accountNo: '', openingBalance: 0, remark: '' }
  createVisible.value = true
}

async function submitCreate() {
  const name = (createForm.value.accountName || '').trim()
  if (!name) {
    ElMessage.warning('请输入账户名称')
    return
  }
  const openingBalance = Number(createForm.value.openingBalance || 0)
  if (openingBalance < 0) {
    ElMessage.warning('期初余额必须 >= 0')
    return
  }
  creating.value = true
  try {
    const a = await createFinanceAccount({
      accountName: name,
      accountNo: createForm.value.accountNo || undefined,
      openingBalance,
      remark: createForm.value.remark || undefined
    })
    ElNotification({ title: '新增成功', message: `账户：${a.accountName}`, type: 'success', duration: 3000 })
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '新增失败')
  } finally {
    creating.value = false
  }
}

onMounted(reload)
</script>

