import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
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
  reverseStatus?: number | null
  reverseBy?: string | null
  reverseTime?: string | null
  reverseWmsBillId?: number | null
  reverseWmsBillNo?: string | null
  createBy?: string | null
  createTime?: string | null
}

export type SalShipItem = {
  id: number
  shipId: number
  orderId: number
  orderDetailId: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  qty?: string | number | null
  returnedQty?: string | number | null
  returnableQty?: string | number | null
}

export type SalShipDetail = { header: SalShip; items: SalShipItem[] }

export async function listSalesShips(params: {
  page: number
  size: number
  keyword?: string
  customerId?: number
  warehouseId?: number
  startDate?: string
  endDate?: string
}) {
  const res = await http.get<PageResponse<SalShip>>('/api/sales/ships', { params })
  return res.data
}

export async function getSalesShip(id: number) {
  const res = await http.get<SalShipDetail>(`/api/sales/ships/${id}`)
  return res.data
}

export type SalShipReverseResponse = {
  shipId: number
  shipNo: string
  reverseStatus: number
  reverseWmsBillId?: number | null
  reverseWmsBillNo?: string | null
}

export async function reverseSalesShip(id: number) {
  const res = await http.post<SalShipReverseResponse>(`/api/sales/ships/${id}/reverse`)
  return res.data
}
