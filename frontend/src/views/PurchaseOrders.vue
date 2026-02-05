<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
        <div style="font-weight: 600">采购订单（记录）</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="keyword" placeholder="搜索单号/供应商" style="width: 240px" clearable @keyup.enter="reload" />
          <el-button @click="reload">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" style="width: 100%" :loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="orderNo" label="单号" width="220" />
      <el-table-column prop="orderDate" label="日期" width="120" />
      <el-table-column prop="supplierName" label="供应商" min-width="180" />
      <el-table-column prop="totalAmount" label="总额" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 3" type="warning">部分入库</el-tag>
          <el-tag v-else-if="row.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="row.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="制单人" width="120" />
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

  <el-dialog v-model="detailVisible" title="采购单详情" width="1000px">
    <div v-if="detailLoading" style="padding: 16px">加载中...</div>
    <div v-else-if="detail">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detail.status === 4" type="success">已完成</el-tag>
          <el-tag v-else-if="detail.status === 3" type="warning">部分入库</el-tag>
          <el-tag v-else-if="detail.status === 2" type="info">已审核</el-tag>
          <el-tag v-else-if="detail.status === 9" type="danger">已作废</el-tag>
          <el-tag v-else type="warning">待审核</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small">
        <el-table-column prop="productCode" label="SKU" width="160" />
        <el-table-column prop="productName" label="商品名称" min-width="240" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="price" label="单价" width="120" />
        <el-table-column prop="qty" label="采购数量" width="120" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="inQty" label="已入库" width="120" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-button type="success" :disabled="!detail?.id || !canReceive(detail)" @click="goReceive(detail!.id)">新增收货批次</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getPurchaseOrder, listPurchaseOrders, type PurOrder, type PurOrderDetail } from '../api/purchase'

const router = useRouter()

const keyword = ref('')
const loading = ref(false)
const rows = ref<PurOrder[]>([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurOrderDetail | null>(null)

async function reload() {
  loading.value = true
  try {
    const res = await listPurchaseOrders({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载采购单失败')
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
    detail.value = await getPurchaseOrder(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function goReceive(orderId: number) {
  detailVisible.value = false
  router.push({ path: '/purchase/inbounds', query: { orderId: String(orderId), openReceive: '1' } })
}

function canReceive(d: PurOrderDetail) {
  return d.status === 2 || d.status === 3
}

onMounted(async () => {
  await reload()
})
</script>
