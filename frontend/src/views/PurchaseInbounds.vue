<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购入库单</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input
            v-model="keyword"
            placeholder="搜索入库单号/采购单号/供应商/WMS单号"
            style="width: 320px"
            clearable
            @keyup.enter="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="inboundNo" label="入库单号" width="210" />
      <el-table-column prop="orderNo" label="采购单号" width="220" />
      <el-table-column prop="supplierName" label="供应商" min-width="160" />
      <el-table-column prop="warehouseName" label="仓库" min-width="140" />
      <el-table-column prop="wmsBillNo" label="WMS单号" width="190" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待执行</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
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

  <el-dialog v-model="detailVisible" title="采购入库单详情" width="980px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="入库单号">{{ detail.inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ detail.inbound.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.inbound.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.inbound.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.inbound.wmsBillNo }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.inbound.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.inbound.createTime }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.inbound.executeBy }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detail.inbound.executeTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.inbound.remark }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin: 12px 0; font-weight: 600">明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="220" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="planQty" label="计划数量" width="130" />
        <el-table-column prop="realQty" label="实际数量" width="130" />
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
import { getPurchaseInbound, listPurchaseInbounds, type PurInbound, type PurInboundDetail } from '../api/purchase'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canView = computed(() => auth.hasPerm('pur:inbound:view'))

const keyword = ref('')
const loading = ref(false)
const rows = ref<PurInbound[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurInboundDetail | null>(null)

async function reload() {
  if (!canView.value) return
  loading.value = true
  try {
    const res = await listPurchaseInbounds({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载入库单失败')
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
    detail.value = await getPurchaseInbound(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  await reload()
})
</script>

