<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">库存流水</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-button v-if="canExport" @click="doExport">导出</el-button>
          <el-select v-model="warehouseId" clearable filterable placeholder="仓库" style="width: 220px">
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="`${w.warehouseName} (${w.warehouseCode})`"
              :value="w.id"
            />
          </el-select>
          <el-select
            v-model="productId"
            clearable
            filterable
            remote
            placeholder="商品（搜索 SKU/名称）"
            style="width: 260px"
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
          <el-input v-model="keyword" placeholder="搜索单号/类型" style="width: 220px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column prop="warehouseName" label="仓库" min-width="150" />
      <el-table-column prop="productCode" label="SKU" width="150" />
      <el-table-column prop="productName" label="商品名称" min-width="220" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          {{ bizTypeLabel(row.bizType) }}
        </template>
      </el-table-column>
      <el-table-column prop="bizNo" label="单号" width="200" />
      <el-table-column prop="changeQty" label="变动数量" width="120" />
      <el-table-column prop="afterStockQty" label="变动后库存" width="120" />
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElLoading, ElMessage, ElNotification } from 'element-plus'
import { listProductOptions, listStockLogs, listWarehouseOptions, type ProductOption, type WarehouseOption, type WmsStockLog } from '../api/wms'
import { wmsExcel } from '../api/wms-excel'
import { useAuthStore } from '../stores/auth'

const bizTypeLabel = (bizType: string) => {
  if (bizType === 'STOCK_IN') return '盘点入库'
  if (bizType === 'STOCK_OUT') return '盘点出库'
  if (bizType === 'CHECK_ADJUST_IN') return '盘点调整入库'
  if (bizType === 'CHECK_ADJUST_OUT') return '盘点调整出库'
  if (bizType === 'PURCHASE_IN') return '采购入库'
  if (bizType === 'PURCHASE_RETURN') return '采购退货'
  if (bizType === 'REVERSAL_IN') return '冲销入库'
  if (bizType === 'REVERSAL_OUT') return '冲销出库'
  return bizType
}

const keyword = ref('')
const warehouseId = ref<number | undefined>(undefined)
const productId = ref<number | undefined>(undefined)
const warehouses = ref<WarehouseOption[]>([])
const productOptions = ref<ProductOption[]>([])
const productLoading = ref(false)
const auth = useAuthStore()
const canExport = computed(() => auth.hasPerm('wms:stocklog:export'))

const loading = ref(false)
const rows = ref<WmsStockLog[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

async function reload() {
  loading.value = true
  try {
    const res = await listStockLogs({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      productId: productId.value
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载库存流水失败')
  } finally {
    loading.value = false
  }
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

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

async function doExport() {
  const loadingSvc = ElLoading.service({ lock: true, text: '正在生成导出文件...', background: 'rgba(0,0,0,0.15)' })
  try {
    await wmsExcel.exportStockLogs({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      productId: productId.value
    })
    ElNotification({ title: '导出已开始', message: '请在浏览器下载列表查看文件', type: 'success', duration: 3000 })
  } catch (e: any) {
    ElNotification({ title: '导出失败', message: e?.message || '导出失败', type: 'error', duration: 6000 })
  } finally {
    loadingSvc.close()
  }
}

onMounted(async () => {
  await Promise.all([
    reload(),
    searchProducts(''),
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
