import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type SalReturn = {
  id: number
  returnNo: string
  customerId: number
  customerCode?: string | null
  customerName?: string | null
  warehouseId: number
  warehouseName?: string | null
  shipId?: number | null
  shipNo?: string | null
  orderId?: number | null
  orderNo?: string | null
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
  receiveBy?: string | null
  receiveTime?: string | null
  qcBy?: string | null
  qcTime?: string | null
  qcDisposition?: string | null
  qcRemark?: string | null
  executeBy?: string | null
  executeTime?: string | null
}

export type SalReturnItem = {
  id: number
  shipDetailId?: number | null
  orderDetailId?: number | null
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  price?: string | number | null
  qty?: string | number | null
  amount?: string | number | null
}

export type SalReturnDetail = { header: SalReturn; items: SalReturnItem[] }

export type SalReturnExecuteResponse = {
  returnId: number
  returnNo: string
  status: number
  wmsBillId?: number | null
  wmsBillNo?: string | null
}

export async function listSalesReturns(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<SalReturn>>('/api/sales/returns', { params })
  return res.data
}

export async function getSalesReturn(id: number) {
  const res = await http.get<SalReturnDetail>(`/api/sales/returns/${id}`)
  return res.data
}

export async function createSalesReturn(payload: {
  shipId: number
  customerId: number
  warehouseId: number
  returnDate?: string
  remark?: string
  lines: { shipDetailId: number; qty: number }[]
}) {
  const res = await http.post<SalReturn>('/api/sales/returns', payload)
  return res.data
}

export async function auditSalesReturn(id: number) {
  const res = await http.post<SalReturn>(`/api/sales/returns/${id}/audit`)
  return res.data
}

export async function executeSalesReturn(id: number) {
  const res = await http.post<SalReturnExecuteResponse>(`/api/sales/returns/${id}/execute`)
  return res.data
}

export async function receiveSalesReturn(id: number) {
  const res = await http.post<SalReturn>(`/api/sales/returns/${id}/receive`)
  return res.data
}

export async function qcRejectSalesReturn(id: number, payload: { disposition: string; remark?: string }) {
  const res = await http.post<SalReturn>(`/api/sales/returns/${id}/qc-reject`, payload)
  return res.data
}

export async function cancelSalesReturn(id: number) {
  const res = await http.post<SalReturn>(`/api/sales/returns/${id}/cancel`)
  return res.data
}
