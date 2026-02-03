import { http } from './http'

export type LedgerEntry = {
  id: number
  entryDate: string
  accountCode: string
  description: string | null
  debit: string
  credit: string
  createdAt: string
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export async function listLedgerEntries(params: { page: number; size: number }) {
  const res = await http.get<PageResponse<LedgerEntry>>('/api/ledger-entries', { params })
  return res.data
}

export async function createLedgerEntry(payload: {
  entryDate: string
  accountCode: string
  description?: string
  debit: number
  credit: number
}) {
  const res = await http.post<LedgerEntry>('/api/ledger-entries', payload)
  return res.data
}

export async function deleteLedgerEntry(id: number) {
  await http.delete(`/api/ledger-entries/${id}`)
}

