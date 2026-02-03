<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div style="font-weight: 600">新增流水</div>
            <el-button :loading="loading" type="primary" @click="submit">保存</el-button>
          </div>
        </template>

        <el-form :model="form" label-width="90px">
          <el-form-item label="日期">
            <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="科目编码">
            <el-input v-model="form.accountCode" placeholder="例如：1001" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="form.description" placeholder="可选" />
          </el-form-item>
          <el-form-item label="借方金额">
            <el-input-number v-model="form.debit" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="贷方金额">
            <el-input-number v-model="form.credit" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="14">
      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div style="font-weight: 600">流水列表</div>
            <el-button :loading="loading" @click="reload">刷新</el-button>
          </div>
        </template>

        <el-table :data="rows" style="width: 100%">
          <el-table-column prop="entryDate" label="日期" width="120" />
          <el-table-column prop="accountCode" label="科目" width="120" />
          <el-table-column prop="description" label="摘要" min-width="180" />
          <el-table-column prop="debit" label="借方" width="120" />
          <el-table-column prop="credit" label="贷方" width="120" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="remove(row.id)">删除</el-button>
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
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createLedgerEntry, deleteLedgerEntry, listLedgerEntries, type LedgerEntry } from '../api/ledger'

const loading = ref(false)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const rows = ref<LedgerEntry[]>([])

const form = reactive({
  entryDate: new Date().toISOString().slice(0, 10),
  accountCode: '',
  description: '',
  debit: 0,
  credit: 0
})

async function reload() {
  loading.value = true
  try {
    const data = await listLedgerEntries({ page: page.value, size: size.value })
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.entryDate || !form.accountCode) {
    ElMessage.warning('请填写日期与科目编码')
    return
  }
  if (form.debit === 0 && form.credit === 0) {
    ElMessage.warning('借方与贷方不能同时为 0')
    return
  }

  loading.value = true
  try {
    await createLedgerEntry({
      entryDate: form.entryDate,
      accountCode: form.accountCode,
      description: form.description || undefined,
      debit: form.debit,
      credit: form.credit
    })
    form.accountCode = ''
    form.description = ''
    form.debit = 0
    form.credit = 0
    ElMessage.success('已保存')
    await reload()
  } finally {
    loading.value = false
  }
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除该条记录？', '提示', { type: 'warning' })
  loading.value = true
  try {
    await deleteLedgerEntry(id)
    ElMessage.success('已删除')
    await reload()
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

onMounted(() => {
  reload()
})
</script>

