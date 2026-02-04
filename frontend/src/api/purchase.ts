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

export async function listPurchaseInbounds(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<PurInbound>>('/api/purchase/inbounds', { params })
  return res.data
}

export async function getPurchaseInbound(id: number) {
  const res = await http.get<PurInboundDetail>(`/api/purchase/inbounds/${id}`)
  return res.data
}

export type PurBatchInboundResponse = {
  inboundId: number
  inboundNo: string
  orderId: number
  orderNo: string
  orderStatus: number
  wmsBillId: number
  wmsBillNo: string
}

export async function batchInboundPurchaseOrder(
  orderId: number,
  payload: { requestNo: string; warehouseId: number; remark?: string; lines: { productId: number; qty: number }[] }
) {
  const res = await http.post<PurBatchInboundResponse>(`/api/purchase/orders/${orderId}/inbounds`, payload)
  return res.data
}

export async function cancelPurchaseOrder(id: number) {
  const res = await http.post<PurOrder>(`/api/purchase/orders/${id}/cancel`)
  return res.data
}
