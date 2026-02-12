import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type FinAccount = {
  id: number
  accountName: string
  accountNo?: string | null
  balance?: string | number | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  updateBy?: string | null
  updateTime?: string | null
}

export type FinAccountOption = {
  id: number
  accountName: string
  balance?: string | number | null
}

export type FinPayment = {
  id: number
  payNo: string
  type: number
  partnerId: number
  partnerName?: string | null
  accountId: number
  accountName?: string | null
  amount?: string | number | null
  bizType: number
  bizId: number
  bizNo?: string | null
  payDate: string
  method?: string | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export async function listFinanceAccounts(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<FinAccount>>('/api/finance/accounts', { params })
  return res.data
}

export async function listFinanceAccountOptions(params: { keyword?: string; limit?: number }) {
  const res = await http.get<FinAccountOption[]>('/api/finance/accounts/options', { params })
  return res.data
}

export async function createFinanceAccount(payload: {
  accountName: string
  accountNo?: string
  openingBalance: number
  remark?: string
}) {
  const res = await http.post<FinAccount>('/api/finance/accounts', payload)
  return res.data
}

export async function listFinancePayments(params: {
  page: number
  size: number
  keyword?: string
  type?: number
  partnerId?: number
  accountId?: number
  startDate?: string
  endDate?: string
}) {
  const res = await http.get<PageResponse<FinPayment>>('/api/finance/payments', { params })
  return res.data
}

export type FinManualPayment = {
  id: number
  manualNo: string
  type: number
  partnerId?: number | null
  accountId: number
  amount?: string | number | null
  payDate: string
  method?: string | null
  bizNo?: string | null
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export async function createManualPayment(payload: {
  type: number
  partnerId?: number
  accountId?: number
  amount: number
  payDate?: string
  method?: string
  bizNo?: string
  remark?: string
}) {
  const res = await http.post<FinManualPayment>('/api/finance/manual-payments', payload)
  return res.data
}

export async function cancelManualPayment(id: number) {
  const res = await http.post<FinManualPayment>(`/api/finance/manual-payments/${id}/cancel`)
  return res.data
}

export type FinTransfer = {
  id: number
  transferNo: string
  fromAccountId: number
  toAccountId: number
  amount?: string | number | null
  transferDate: string
  remark?: string | null
  status: number
  createBy?: string | null
  createTime?: string | null
  cancelBy?: string | null
  cancelTime?: string | null
}

export async function createTransfer(payload: {
  fromAccountId: number
  toAccountId: number
  amount: number
  transferDate?: string
  remark?: string
}) {
  const res = await http.post<FinTransfer>('/api/finance/transfers', payload)
  return res.data
}

export async function cancelTransfer(id: number) {
  const res = await http.post<FinTransfer>(`/api/finance/transfers/${id}/cancel`)
  return res.data
}
