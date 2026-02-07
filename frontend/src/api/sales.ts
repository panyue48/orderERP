import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type SalOrder = {
  id: number
  orderNo: string
  customerId: number
  customerCode?: string | null
  customerName?: string | null
  warehouseId: number
  warehouseName?: string | null
  orderDate: string
  totalAmount?: string | number | null
  status: number
  remark?: string | null
  wmsBillId?: number | null
  wmsBillNo?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
  shipBy?: string | null
  shipTime?: string | null
}

export type SalOrderItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  price?: string | number | null
  qty?: string | number | null
  shippedQty?: string | number | null
  amount?: string | number | null
}

export type SalOrderDetail = {
  id: number
  orderNo: string
  customerId: number
  customerCode?: string | null
  customerName?: string | null
  warehouseId: number
  warehouseName?: string | null
  orderDate: string
  totalAmount?: string | number | null
  status: number
  remark?: string | null
  wmsBillId?: number | null
  wmsBillNo?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
  shipBy?: string | null
  shipTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
  items: SalOrderItem[]
}

export type SalShip = {
  id: number
  shipNo: string
  orderId: number
  orderNo?: string | null
  customerId: number
  customerCode?: string | null
  customerName?: string | null
  warehouseId: number
  warehouseName?: string | null
  shipTime?: string | null
  totalQty?: string | number | null
  wmsBillId?: number | null
  wmsBillNo?: string | null
  createBy?: string | null
  createTime?: string | null
}

export type SalCreditUsage = {
  customerId: number
  creditLimit?: string | number | null
  usedAmount?: string | number | null
  availableAmount?: string | number | null
  outstandingArAmount?: string | number | null
  unbilledNetAmount?: string | number | null
  openOrderReservedAmount?: string | number | null
  enabled?: boolean | null
}

export async function listSalesOrderShips(orderId: number) {
  const res = await http.get<SalShip[]>(`/api/sales/orders/${orderId}/ships`)
  return res.data
}

export async function listSalesOrders(params: {
  page: number
  size: number
  keyword?: string
  customerId?: number
  startDate?: string
  endDate?: string
}) {
  const res = await http.get<PageResponse<SalOrder>>('/api/sales/orders', { params })
  return res.data
}

export async function getSalesOrder(id: number) {
  const res = await http.get<SalOrderDetail>(`/api/sales/orders/${id}`)
  return res.data
}

export async function createSalesOrder(payload: {
  customerId: number
  warehouseId: number
  orderDate?: string
  remark?: string
  lines: { productId: number; qty: number; price?: number | null }[]
}) {
  const res = await http.post<SalOrder>('/api/sales/orders', payload)
  return res.data
}

export async function quickShipSalesOrder(payload: {
  customerId: number
  warehouseId: number
  orderDate?: string
  remark?: string
  lines: { productId: number; qty: number; price?: number | null }[]
}) {
  const res = await http.post<SalOrder>('/api/sales/orders/quick-ship', payload)
  return res.data
}

export async function quickAuditSalesOrder(payload: {
  customerId: number
  warehouseId: number
  orderDate?: string
  remark?: string
  lines: { productId: number; qty: number; price?: number | null }[]
}) {
  const res = await http.post<SalOrder>('/api/sales/orders/quick-audit', payload)
  return res.data
}

export async function auditSalesOrder(id: number) {
  const res = await http.post<SalOrder>(`/api/sales/orders/${id}/audit`)
  return res.data
}

export async function shipSalesOrder(id: number) {
  const res = await http.post<SalOrder>(`/api/sales/orders/${id}/ship`)
  return res.data
}

export async function shipSalesOrderBatch(
  id: number,
  payload: { requestNo: string; lines: { orderDetailId: number; productId: number; qty: number }[] }
) {
  const res = await http.post<SalOrder>(`/api/sales/orders/${id}/ships`, payload)
  return res.data
}

export async function cancelSalesOrder(id: number) {
  const res = await http.post<SalOrder>(`/api/sales/orders/${id}/cancel`)
  return res.data
}

export async function deleteSalesOrderDraft(id: number) {
  const res = await http.post<void>(`/api/sales/orders/${id}/delete`)
  return res.data
}

export async function getSalesCustomerCreditUsage(customerId: number) {
  const res = await http.get<SalCreditUsage>(`/api/sales/credit/customers/${customerId}`)
  return res.data
}

export async function listSalesCustomerCreditUsage(customerIds: number[]) {
  const ids = (customerIds || []).filter(Boolean)
  if (ids.length === 0) return []
  const res = await http.get<SalCreditUsage[]>(`/api/sales/credit/customers`, { params: { customerIds: ids.join(',') } })
  return res.data
}
