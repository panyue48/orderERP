<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">库存查询</div>
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
          <el-input
            v-model="keyword"
            placeholder="搜索 SKU / 商品名 / 仓库"
            style="width: 240px"
            clearable
            @keyup.enter="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="warehouseName" label="仓库" min-width="160" />
      <el-table-column prop="productCode" label="SKU" width="160" />
      <el-table-column prop="productName" label="商品名称" min-width="220" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="stockQty" label="物理库存" width="120" />
      <el-table-column prop="qcQty" label="待质检" width="120" />
      <el-table-column prop="lockedQty" label="锁定库存" width="120" />
      <el-table-column prop="availableQty" label="可用库存" width="120" />
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElLoading, ElMessage, ElNotification } from 'element-plus'
import { listStocks, listWarehouseOptions, type WarehouseOption, type WmsStock } from '../api/wms'
import { wmsExcel } from '../api/wms-excel'
import { useAuthStore } from '../stores/auth'

const keyword = ref('')
const warehouseId = ref<number | undefined>(undefined)
const warehouses = ref<WarehouseOption[]>([])
const auth = useAuthStore()
const canExport = computed(() => auth.hasPerm('wms:stock:export'))

const loading = ref(false)
const rows = ref<WmsStock[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

async function reload() {
  loading.value = true
  try {
    const res = await listStocks({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value
    })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载库存失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p - 1
  reload()
}

async function doExport() {
  const loadingSvc = ElLoading.service({ lock: true, text: '正在生成导出文件...', background: 'rgba(0,0,0,0.15)' })
  try {
    await wmsExcel.exportStocks({ keyword: keyword.value || undefined, warehouseId: warehouseId.value })
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
