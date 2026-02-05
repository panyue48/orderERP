import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type WarehouseOption = { id: number; warehouseCode: string; warehouseName: string }
export type ProductOption = { id: number; categoryId?: number | null; productCode: string; productName: string; unit?: string | null }

export type WmsStock = {
  id: number
  warehouseId: number
  warehouseName?: string | null
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  stockQty?: string | number | null
  lockedQty?: string | number | null
  qcQty?: string | number | null
  availableQty?: string | number | null
  updateTime?: string | null
}

export async function listStocks(params: { page: number; size: number; keyword?: string; warehouseId?: number }) {
  const res = await http.get<PageResponse<WmsStock>>('/api/wms/stocks', { params })
  return res.data
}

export type StockInBill = {
  id: number
  billNo: string
  warehouseId: number
  warehouseName?: string | null
  status: number
  totalQty?: string | number | null
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
}

export type StockInBillItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  qty?: string | number | null
  realQty?: string | number | null
}

export type StockInBillDetail = StockInBill & { items: StockInBillItem[] }

export async function listStockInBills(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<StockInBill>>('/api/wms/stock-in-bills', { params })
  return res.data
}

export async function getStockInBill(id: number) {
  const res = await http.get<StockInBillDetail>(`/api/wms/stock-in-bills/${id}`)
  return res.data
}

export type WmsBillPrecheckLine = {
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  qty?: string | number | null
  stockQty?: string | number | null
  lockedQty?: string | number | null
  availableQty?: string | number | null
  ok: boolean
  message?: string | null
}

export type WmsBillPrecheckResponse = {
  ok: boolean
  message?: string | null
  lines: WmsBillPrecheckLine[]
}

export async function precheckStockInBill(id: number) {
  const res = await http.get<WmsBillPrecheckResponse>(`/api/wms/stock-in-bills/${id}/precheck`)
  return res.data
}

export async function precheckStockOutBill(id: number) {
  const res = await http.get<WmsBillPrecheckResponse>(`/api/wms/stock-out-bills/${id}/precheck`)
  return res.data
}

export async function createStockInBill(payload: { warehouseId: number; remark?: string; lines: { productId: number; qty: number }[] }) {
  const res = await http.post<StockInBill>('/api/wms/stock-in-bills', payload)
  return res.data
}

export async function executeStockInBill(id: number) {
  const res = await http.post<StockInBill>(`/api/wms/stock-in-bills/${id}/execute`)
  return res.data
}

export type WmsReverseResponse = { reversalBillId: number; reversalBillNo: string }

export async function reverseStockInBill(id: number) {
  const res = await http.post<WmsReverseResponse>(`/api/wms/stock-in-bills/${id}/reverse`)
  return res.data
}

export async function reverseStockOutBill(id: number) {
  const res = await http.post<WmsReverseResponse>(`/api/wms/stock-out-bills/${id}/reverse`)
  return res.data
}

export async function listStockOutBills(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<StockInBill>>('/api/wms/stock-out-bills', { params })
  return res.data
}

export async function getStockOutBill(id: number) {
  const res = await http.get<StockInBillDetail>(`/api/wms/stock-out-bills/${id}`)
  return res.data
}

export async function createStockOutBill(payload: { warehouseId: number; remark?: string; lines: { productId: number; qty: number }[] }) {
  const res = await http.post<StockInBill>('/api/wms/stock-out-bills', payload)
  return res.data
}

export async function executeStockOutBill(id: number) {
  const res = await http.post<StockInBill>(`/api/wms/stock-out-bills/${id}/execute`)
  return res.data
}

export type WmsStockLog = {
  id: number
  warehouseId: number
  warehouseName?: string | null
  productId: number
  productCode?: string | null
  productName?: string | null
  bizType: string
  bizNo?: string | null
  changeQty?: string | number | null
  afterStockQty?: string | number | null
  createTime?: string | null
}

export async function listStockLogs(params: { page: number; size: number; keyword?: string; warehouseId?: number; productId?: number }) {
  const res = await http.get<PageResponse<WmsStockLog>>('/api/wms/stock-logs', { params })
  return res.data
}

export async function listWarehouseOptions(params?: { keyword?: string; limit?: number }) {
  const res = await http.get<WarehouseOption[]>('/api/base/warehouses/options', { params })
  return res.data
}

export async function listProductOptions(params?: { keyword?: string; limit?: number }) {
  const res = await http.get<ProductOption[]>('/api/base/products/options', { params })
  return res.data
}

export type WmsCheckBill = {
  id: number
  billNo: string
  warehouseId: number
  warehouseName?: string | null
  status: number
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  executeTime?: string | null
}

export type WmsCheckBillItem = {
  id: number
  productId: number
  productCode?: string | null
  productName?: string | null
  unit?: string | null
  countedQty?: string | number | null
  bookQty?: string | number | null
  diffQty?: string | number | null
}

export type WmsCheckBillDetail = WmsCheckBill & { items: WmsCheckBillItem[] }

export async function listCheckBills(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<WmsCheckBill>>('/api/wms/check-bills', { params })
  return res.data
}

export async function getCheckBill(id: number) {
  const res = await http.get<WmsCheckBillDetail>(`/api/wms/check-bills/${id}`)
  return res.data
}

export async function createCheckBill(payload: {
  warehouseId: number
  remark?: string
  lines: { productId: number; countedQty: number }[]
}) {
  const res = await http.post<WmsCheckBill>('/api/wms/check-bills', payload)
  return res.data
}

export type WmsCheckExecuteResponse = {
  checkBillId: number
  checkBillNo: string
  stockInBillId?: number | null
  stockInBillNo?: string | null
  stockOutBillId?: number | null
  stockOutBillNo?: string | null
}

export async function executeCheckBill(id: number) {
  const res = await http.post<WmsCheckExecuteResponse>(`/api/wms/check-bills/${id}/execute`)
  return res.data
}
