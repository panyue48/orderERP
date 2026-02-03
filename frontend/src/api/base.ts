import { http } from './http'

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type BaseProduct = {
  id: number
  categoryId?: number | null
  productCode: string
  productName: string
  barcode?: string | null
  spec?: string | null
  unit?: string | null
  weight?: string | number | null
  purchasePrice?: string | number | null
  salePrice?: string | number | null
  lowStock?: number | null
  imageUrl?: string | null
  status?: number | null
}

export type BaseCategory = {
  id: number
  parentId?: number | null
  categoryCode: string
  categoryName: string
  sort?: number | null
  status?: number | null
}

export type BaseWarehouse = {
  id: number
  warehouseCode: string
  warehouseName: string
  location?: string | null
  manager?: string | null
  status?: number | null
}

export type BasePartner = {
  id: number
  partnerName: string
  partnerCode: string
  type: number // 1 supplier, 2 customer
  contact?: string | null
  phone?: string | null
  email?: string | null
  creditLimit?: string | number | null
  status?: number | null
}

export async function listProducts(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<BaseProduct>>('/api/base/products', { params })
  return res.data
}

export async function createProduct(payload: Partial<BaseProduct> & { productCode: string; productName: string }) {
  const res = await http.post<BaseProduct>('/api/base/products', payload)
  return res.data
}

export async function updateProduct(id: number, payload: Partial<BaseProduct>) {
  const res = await http.put<BaseProduct>(`/api/base/products/${id}`, payload)
  return res.data
}

export async function deleteProduct(id: number) {
  await http.delete(`/api/base/products/${id}`)
}

export async function listCategories(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<BaseCategory>>('/api/base/categories', { params })
  return res.data
}

export async function createCategory(payload: Partial<BaseCategory> & { categoryCode: string; categoryName: string }) {
  const res = await http.post<BaseCategory>('/api/base/categories', payload)
  return res.data
}

export async function updateCategory(id: number, payload: Partial<BaseCategory>) {
  const res = await http.put<BaseCategory>(`/api/base/categories/${id}`, payload)
  return res.data
}

export async function deleteCategory(id: number) {
  await http.delete(`/api/base/categories/${id}`)
}

export async function listCategoryOptions(params?: { keyword?: string; limit?: number }) {
  const res = await http.get<{ id: number; parentId: number; categoryCode: string; categoryName: string }[]>(
    '/api/base/categories/options',
    { params }
  )
  return res.data
}

export async function listWarehouses(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<BaseWarehouse>>('/api/base/warehouses', { params })
  return res.data
}

export async function createWarehouse(payload: Partial<BaseWarehouse> & { warehouseCode: string; warehouseName: string }) {
  const res = await http.post<BaseWarehouse>('/api/base/warehouses', payload)
  return res.data
}

export async function updateWarehouse(id: number, payload: Partial<BaseWarehouse>) {
  const res = await http.put<BaseWarehouse>(`/api/base/warehouses/${id}`, payload)
  return res.data
}

export async function deleteWarehouse(id: number) {
  await http.delete(`/api/base/warehouses/${id}`)
}

export async function listPartners(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<BasePartner>>('/api/base/partners', { params })
  return res.data
}

export async function createPartner(payload: Partial<BasePartner> & { partnerName: string; partnerCode: string; type: number }) {
  const res = await http.post<BasePartner>('/api/base/partners', payload)
  return res.data
}

export async function updatePartner(id: number, payload: Partial<BasePartner>) {
  const res = await http.put<BasePartner>(`/api/base/partners/${id}`, payload)
  return res.data
}

export async function deletePartner(id: number) {
  await http.delete(`/api/base/partners/${id}`)
}
