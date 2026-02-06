import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type SalArBill = {
  id: number
  billNo: string
  customerId: number
  customerCode?: string | null
  customerName?: string | null
  startDate: string
  endDate: string
  totalAmount?: string | number | null
  receivedAmount?: string | number | null
  invoiceAmount?: string | number | null
  status: number
  remark?: string | null
  createBy?: string | null
  createTime?: string | null
  auditBy?: string | null
  auditTime?: string | null
}

export type SalArBillDoc = {
  id: number
  docType: number
  docId: number
  docNo: string
  orderId?: number | null
  orderNo?: string | null
  docTime?: string | null
  amount?: string | number | null
  productSummary?: string | null
}

export type SalArReceipt = {
  id: number
  receiptNo: string
  billId: number
  customerId: number
  receiptDate: string
  amount?: string | number | null
  method?: string | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export type SalArInvoice = {
  id: number
  invoiceNo: string
  billId: number
  customerId: number
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

export type SalArBillDetail = {
  bill: SalArBill
  docs: SalArBillDoc[]
  receipts: SalArReceipt[]
  invoices: SalArInvoice[]
}

export async function listSalesArBills(params: {
  page: number
  size: number
  keyword?: string
  customerId?: number
  startDate?: string
  endDate?: string
}) {
  const res = await http.get<PageResponse<SalArBill>>('/api/sales/ar-bills', { params })
  return res.data
}

export async function getSalesArBill(id: number) {
  const res = await http.get<SalArBillDetail>(`/api/sales/ar-bills/${id}`)
  return res.data
}

export async function createSalesArBill(payload: { customerId: number; startDate: string; endDate: string; remark?: string }) {
  const res = await http.post<SalArBill>('/api/sales/ar-bills', payload)
  return res.data
}

export async function auditSalesArBill(id: number) {
  const res = await http.post<SalArBill>(`/api/sales/ar-bills/${id}/audit`)
  return res.data
}

export async function regenerateSalesArBill(id: number) {
  const res = await http.post<SalArBill>(`/api/sales/ar-bills/${id}/regenerate`)
  return res.data
}

export async function cancelSalesArBill(id: number) {
  const res = await http.post<SalArBill>(`/api/sales/ar-bills/${id}/cancel`)
  return res.data
}

export async function addSalesArReceipt(
  billId: number,
  payload: { receiptDate?: string; amount: number; method?: string; remark?: string }
) {
  const res = await http.post<SalArReceipt>(`/api/sales/ar-bills/${billId}/receipts`, payload)
  return res.data
}

export async function cancelSalesArReceipt(billId: number, receiptId: number) {
  const res = await http.post<SalArReceipt>(`/api/sales/ar-bills/${billId}/receipts/${receiptId}/cancel`)
  return res.data
}

export async function addSalesArInvoice(
  billId: number,
  payload: { invoiceNo: string; invoiceDate?: string; amount: number; taxAmount?: number; remark?: string }
) {
  const res = await http.post<SalArInvoice>(`/api/sales/ar-bills/${billId}/invoices`, payload)
  return res.data
}

export async function cancelSalesArInvoice(billId: number, invoiceId: number) {
  const res = await http.post<SalArInvoice>(`/api/sales/ar-bills/${billId}/invoices/${invoiceId}/cancel`)
  return res.data
}

