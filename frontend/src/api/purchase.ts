import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type PurOrder = {
  id: number
  orderNo: string
  supplierId: number
  supplierCode?: string | null
  supplierName?: string | null
  orderDate: string
  totalAmount?: string | number | null
  payAmount?: string | number | null
  status: number
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
}

export type PurOrderItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  price?: string | number | null
  qty?: string | number | null
  amount?: string | number | null
  inQty?: string | number | null
}

export type PurOrderDetail = PurOrder & { items: PurOrderItem[] }

export async function listPurchaseOrders(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<PurOrder>>('/api/purchase/orders', { params })
  return res.data
}

export async function getPurchaseOrder(id: number) {
  const res = await http.get<PurOrderDetail>(`/api/purchase/orders/${id}`)
  return res.data
}

export type PurOrderOption = {
  id: number
  orderNo: string
  supplierId: number
  supplierCode?: string | null
  supplierName?: string | null
  orderDate?: string | null
  status: number
}

export async function listPurchaseOrderOptions(params?: { keyword?: string; limit?: number }) {
  const res = await http.get<PurOrderOption[]>('/api/purchase/orders/options', { params })
  return res.data
}

export type PurPendingQcItem = {
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  qty?: string | number | null
}

export type PurPendingQcSummary = {
  orderId: number
  pendingCount?: number | null
  pendingQty?: string | number | null
  items: PurPendingQcItem[]
}

export async function getPurchaseOrderPendingQcSummary(orderId: number) {
  const res = await http.get<PurPendingQcSummary>(`/api/purchase/orders/${orderId}/pending-qc-summary`)
  return res.data
}

export async function createPurchaseOrder(payload: {
  supplierId: number
  orderDate?: string
  remark?: string
  lines: { productId: number; price: number; qty: number }[]
}) {
  const res = await http.post<PurOrder>('/api/purchase/orders', payload)
  return res.data
}

export async function auditPurchaseOrder(id: number) {
  const res = await http.post<PurOrder>(`/api/purchase/orders/${id}/audit`)
  return res.data
}

export type PurInboundResponse = {
  orderId: number
  orderNo: string
  orderStatus: number
  wmsBillId: number
  wmsBillNo: string
}

export async function inboundPurchaseOrder(id: number, payload: { warehouseId: number }) {
  const res = await http.post<PurInboundResponse>(`/api/purchase/orders/${id}/inbound`, payload)
  return res.data
}

export type PurInbound = {
  id: number
  inboundNo: string
  requestNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierCode?: string | null
  supplierName?: string | null
  warehouseId: number
  warehouseName?: string | null
  status: number
  qcStatus?: number | null
  qcBy?: string | null
  qcTime?: string | null
  qcRemark?: string | null
  wmsBillId?: number | null
  wmsBillNo?: string | null
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  executeBy?: string | null
  executeTime?: string | null
}

export type PurInboundItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  planQty?: string | number | null
  realQty?: string | number | null
}

export type PurInboundDetail = { inbound: PurInbound; items: PurInboundItem[] }

export async function listPurchaseInbounds(params: { page: number; size: number; keyword?: string; orderId?: number }) {
  const res = await http.get<PageResponse<PurInbound>>('/api/purchase/inbounds', { params })
  return res.data
}

export async function getPurchaseInbound(id: number) {
  const res = await http.get<PurInboundDetail>(`/api/purchase/inbounds/${id}`)
  return res.data
}

export async function createPurchaseInbound(payload: {
  requestNo: string
  supplierId: number
  orderDate?: string
  warehouseId: number
  remark?: string
  lines: { productId: number; price: number; qty: number; inboundQty: number }[]
}) {
  const res = await http.post<PurBatchInboundResponse>('/api/purchase/inbounds', payload)
  return res.data
}

export type PurBatchInboundResponse = {
  inboundId: number
  inboundNo: string
  orderId: number
  orderNo: string
  orderStatus: number
  wmsBillId?: number | null
  wmsBillNo?: string | null
}

export async function batchInboundPurchaseOrder(
  orderId: number,
  payload: { requestNo: string; warehouseId: number; remark?: string; lines: { productId: number; qty: number }[] }
) {
  const res = await http.post<PurBatchInboundResponse>(`/api/purchase/orders/${orderId}/inbounds`, payload)
  return res.data
}

export async function iqcPassPurchaseInbound(id: number, payload?: { remark?: string }) {
  const res = await http.post<PurBatchInboundResponse>(`/api/purchase/inbounds/${id}/iqc-pass`, payload || {})
  return res.data
}

export async function iqcRejectPurchaseInbound(id: number, payload?: { remark?: string }) {
  const res = await http.post<PurInbound>(`/api/purchase/inbounds/${id}/iqc-reject`, payload || {})
  return res.data
}

export async function cancelPurchaseOrder(id: number) {
  const res = await http.post<PurOrder>(`/api/purchase/orders/${id}/cancel`)
  return res.data
}

// =======================
// Purchase AP Bills (reconciliation / invoice / payment)
// =======================

export type PurApBill = {
  id: number
  billNo: string
  supplierId: number
  supplierCode?: string | null
  supplierName?: string | null
  startDate: string
  endDate: string
  totalAmount?: string | number | null
  paidAmount?: string | number | null
  invoiceAmount?: string | number | null
  status: number
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
}

export type PurApBillDoc = {
  id: number
  docType: number
  docId: number
  docNo: string
  orderId?: number | null
  orderNo?: string | null
  docTime?: string | null
  amount?: string | number | null
}

export type PurApPayment = {
  id: number
  payNo: string
  billId: number
  supplierId: number
  payDate: string
  amount?: string | number | null
  method?: string | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export type PurApInvoice = {
  id: number
  invoiceNo: string
  billId: number
  supplierId: number
  invoiceDate: string
  amount?: string | number | null
  taxAmount?: string | number | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export type PurApBillDetail = {
  bill: PurApBill
  docs: PurApBillDoc[]
  payments: PurApPayment[]
  invoices: PurApInvoice[]
}

export async function listPurchaseApBills(params: {
  page: number
  size: number
  keyword?: string
  supplierId?: number
  startDate?: string
  endDate?: string
}) {
  const res = await http.get<PageResponse<PurApBill>>('/api/purchase/ap-bills', { params })
  return res.data
}

export async function getPurchaseApBill(id: number) {
  const res = await http.get<PurApBillDetail>(`/api/purchase/ap-bills/${id}`)
  return res.data
}

export async function createPurchaseApBill(payload: { supplierId: number; startDate: string; endDate: string; remark?: string }) {
  const res = await http.post<PurApBill>('/api/purchase/ap-bills', payload)
  return res.data
}

export async function auditPurchaseApBill(id: number) {
  const res = await http.post<PurApBill>(`/api/purchase/ap-bills/${id}/audit`)
  return res.data
}

export async function regeneratePurchaseApBill(id: number) {
  const res = await http.post<PurApBill>(`/api/purchase/ap-bills/${id}/regenerate`)
  return res.data
}

export async function cancelPurchaseApBill(id: number) {
  const res = await http.post<PurApBill>(`/api/purchase/ap-bills/${id}/cancel`)
  return res.data
}

export async function addPurchaseApPayment(
  billId: number,
  payload: { payDate?: string; amount: number; method?: string; remark?: string }
) {
  const res = await http.post<PurApPayment>(`/api/purchase/ap-bills/${billId}/payments`, payload)
  return res.data
}

export async function cancelPurchaseApPayment(billId: number, paymentId: number) {
  const res = await http.post<PurApPayment>(`/api/purchase/ap-bills/${billId}/payments/${paymentId}/cancel`)
  return res.data
}

export async function addPurchaseApInvoice(
  billId: number,
  payload: { invoiceNo: string; invoiceDate?: string; amount: number; taxAmount?: number; remark?: string }
) {
  const res = await http.post<PurApInvoice>(`/api/purchase/ap-bills/${billId}/invoices`, payload)
  return res.data
}

export async function cancelPurchaseApInvoice(billId: number, invoiceId: number) {
  const res = await http.post<PurApInvoice>(`/api/purchase/ap-bills/${billId}/invoices/${invoiceId}/cancel`)
  return res.data
}

export type PurReturn = {
  id: number
  returnNo: string
  supplierId: number
  supplierCode?: string | null
  supplierName?: string | null
  warehouseId: number
  warehouseName?: string | null
  returnDate: string
  totalQty?: string | number | null
  totalAmount?: string | number | null
  status: number
  remark?: string | null
  wmsBillId?: number | null
  wmsBillNo?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
  executeBy?: string | null
  executeTime?: string | null
}

export type PurReturnItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  price?: string | number | null
  qty?: string | number | null
  amount?: string | number | null
}

export type PurReturnDetail = { header: PurReturn; items: PurReturnItem[] }

export async function listPurchaseReturns(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<PurReturn>>('/api/purchase/returns', { params })
  return res.data
}

export async function getPurchaseReturn(id: number) {
  const res = await http.get<PurReturnDetail>(`/api/purchase/returns/${id}`)
  return res.data
}

export async function createPurchaseReturn(payload: {
  supplierId: number
  warehouseId: number
  returnDate?: string
  remark?: string
  lines: { productId: number; price: number; qty: number }[]
}) {
  const res = await http.post<PurReturn>('/api/purchase/returns', payload)
  return res.data
}

export async function auditPurchaseReturn(id: number) {
  const res = await http.post<PurReturn>(`/api/purchase/returns/${id}/audit`)
  return res.data
}

export type PurReturnExecuteResponse = {
  returnId: number
  returnNo: string
  returnStatus: number
  wmsBillId: number
  wmsBillNo: string
}

export async function executePurchaseReturn(id: number) {
  const res = await http.post<PurReturnExecuteResponse>(`/api/purchase/returns/${id}/execute`)
  return res.data
}

export async function cancelPurchaseReturn(id: number) {
  const res = await http.post<PurReturn>(`/api/purchase/returns/${id}/cancel`)
  return res.data
}
