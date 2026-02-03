<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">盘点出库</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
          <el-button v-if="canAdd" type="primary" @click="openCreate">新增出库单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="billNo" label="单号" width="190" />
      <el-table-column prop="warehouseName" label="仓库" min-width="160" />
      <el-table-column prop="totalQty" label="数量合计" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="success">已完成</el-tag>
          <el-tag v-else type="warning">待执行</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-if="canExecute && row.status === 1" link type="danger" @click="openPrecheck(row.id, row.billNo)">
            执行
          </el-button>
          <el-popconfirm
            v-if="canReverse && row.status === 2"
            :title="`确认冲销出库单 ${row.billNo} 吗？将生成一张冲销单并回滚库存。`"
            confirm-button-text="冲销"
            cancel-button-text="取消"
            confirm-button-type="danger"
            width="360"
            @confirm="reverse(row.id, row.billNo)"
          >
            <template #reference>
              <el-button link type="danger" :loading="reversingId === row.id">冲销</el-button>
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

  <el-dialog v-model="precheckVisible" title="执行前校验" width="980px">
    <div v-if="precheckLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="precheck">
      <el-alert
        v-if="precheck.ok"
        type="success"
        :closable="false"
        title="校验通过，可执行"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-alert
        v-else
        type="error"
        :closable="false"
        :title="precheck.message || '校验未通过'"
        show-icon
        style="margin-bottom: 12px"
      />

      <el-table :data="precheck.lines" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="220" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="qty" label="数量" width="120" />
        <el-table-column prop="stockQty" label="物理库存" width="120" />
        <el-table-column prop="lockedQty" label="锁定" width="120" />
        <el-table-column prop="availableQty" label="可用" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.ok" type="success">通过</el-tag>
            <el-tag v-else type="danger">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="原因" min-width="160" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="precheckVisible = false">取消</el-button>
      <el-button type="danger" :loading="executingId === precheckBillId" :disabled="!precheck?.ok" @click="executePrechecked">
        确认执行
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="createVisible" title="新增盘点出库单" width="900px">
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="仓库">
        <el-select v-model="createForm.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 320px">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" placeholder="可选" />
      </el-form-item>

      <el-form-item label="明细">
        <div style="width: 100%">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
            <div style="color: #666">至少 1 行，数量 > 0</div>
            <el-button @click="addLine">添加行</el-button>
          </div>

          <el-table :data="createForm.lines" size="small" border>
            <el-table-column label="商品" min-width="420">
              <template #default="{ row }">
                <el-select
                  v-model="row.productId"
                  filterable
                  remote
                  clearable
                  placeholder="搜索 SKU / 商品名"
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
            <el-table-column label="数量" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="0" :precision="3" :step="1" />
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
      <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="盘点出库单详情" width="900px">
    <div v-if="detail">
      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px 16px; margin-bottom: 12px">
        <div><span style="color: #666">单号：</span>{{ detail.billNo }}</div>
        <div><span style="color: #666">仓库：</span>{{ detail.warehouseName }}</div>
        <div>
          <span style="color: #666">状态：</span>
          <el-tag v-if="detail.status === 2" type="success">已完成</el-tag>
          <el-tag v-else type="warning">待执行</el-tag>
        </div>
        <div><span style="color: #666">数量合计：</span>{{ detail.totalQty }}</div>
        <div><span style="color: #666">创建人：</span>{{ detail.createBy || '-' }}</div>
        <div><span style="color: #666">创建时间：</span>{{ detail.createTime || '-' }}</div>
        <div style="grid-column: 1 / span 3"><span style="color: #666">备注：</span>{{ detail.remark || '-' }}</div>
      </div>
      <el-table :data="detail.items" border>
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="220" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="qty" label="应发数量" width="140" />
        <el-table-column prop="realQty" label="实发数量" width="140" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createStockOutBill,
  executeStockOutBill,
  getStockOutBill,
  listProductOptions,
  listStockOutBills,
  listWarehouseOptions,
  precheckStockOutBill,
  reverseStockOutBill,
  type ProductOption,
  type StockInBill,
  type StockInBillDetail,
  type WarehouseOption,
  type WmsBillPrecheckResponse
} from '../api/wms'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canAdd = computed(() => auth.hasPerm('wms:stockout:add'))
const canExecute = computed(() => auth.hasPerm('wms:stockout:execute'))
const canReverse = computed(() => auth.hasPerm('wms:stockout:reverse'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<StockInBill[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const warehouses = ref<WarehouseOption[]>([])

const createVisible = ref(false)
const saving = ref(false)
const createForm = ref<{ warehouseId?: number; remark?: string; lines: { productId?: number; qty: number }[] }>({
  warehouseId: undefined,
  remark: '',
  lines: [{ productId: undefined, qty: 1 }]
})

const productLoading = ref(false)
const productOptions = ref<ProductOption[]>([])

const detailVisible = ref(false)
const detail = ref<StockInBillDetail | null>(null)
const executingId = ref<number | null>(null)
const reversingId = ref<number | null>(null)

const precheckVisible = ref(false)
const precheckLoading = ref(false)
const precheck = ref<WmsBillPrecheckResponse | null>(null)
const precheckBillId = ref<number | null>(null)
const precheckBillNo = ref<string>('')

async function reload() {
  loading.value = true
  try {
    const res = await listStockOutBills({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载出库单失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

function openCreate() {
  createForm.value = { warehouseId: undefined, remark: '', lines: [{ productId: undefined, qty: 1 }] }
  createVisible.value = true
  searchProducts('')
}

function addLine() {
  createForm.value.lines.push({ productId: undefined, qty: 1 })
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
  if (!createForm.value.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  if (!createForm.value.lines.length) {
    ElMessage.warning('请至少添加 1 行明细')
    return
  }
  const lines = createForm.value.lines
    .filter((l) => l.productId && Number(l.qty) > 0)
    .map((l) => ({ productId: l.productId as number, qty: Number(l.qty) }))
  if (!lines.length) {
    ElMessage.warning('请填写商品与数量')
    return
  }

  saving.value = true
  try {
    await createStockOutBill({ warehouseId: createForm.value.warehouseId, remark: createForm.value.remark || undefined, lines })
    ElMessage.success('已创建')
    createVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function execute(id: number, billNo: string) {
  try {
    executingId.value = id
    await executeStockOutBill(id)
    ElMessage.success('已执行')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function openPrecheck(id: number, billNo: string) {
  precheckVisible.value = true
  precheckLoading.value = true
  precheck.value = null
  precheckBillId.value = id
  precheckBillNo.value = billNo
  try {
    precheck.value = await precheckStockOutBill(id)
  } catch (e: any) {
    precheck.value = { ok: false, message: e?.response?.data?.message || '校验失败', lines: [] }
  } finally {
    precheckLoading.value = false
  }
}

async function executePrechecked() {
  if (!precheckBillId.value) return
  if (!precheck.value?.ok) return
  await execute(precheckBillId.value, precheckBillNo.value)
  precheckVisible.value = false
}

async function reverse(id: number, billNo: string) {
  try {
    reversingId.value = id
    const res = await reverseStockOutBill(id)
    ElMessage.success(`已冲销：${res.reversalBillNo}`)
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '冲销失败')
  } finally {
    reversingId.value = null
  }
}

async function openDetail(id: number) {
  detailVisible.value = true
  detail.value = null
  try {
    detail.value = await getStockOutBill(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  }
}

onMounted(async () => {
  await Promise.all([
    reload(),
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
