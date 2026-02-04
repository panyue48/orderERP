import { http } from './http'

export type ImportResult = {
  total: number
  inserted: number
  updated: number
  failed: number
  errors: { rowNum: number; message: string }[]
}

async function downloadBlob(url: string, filename: string, params?: any) {
  // 这里故意不让 axios 直接抛非 2xx 异常：这样才能读取 Blob 并解析后端返回的 JSON 错误信息。
  const res = await http.get(url, {
    params,
    responseType: 'blob',
    validateStatus: () => true
  })

  const status = res.status
  const contentType = (res.headers['content-type'] || '').toString()

  if (status < 200 || status >= 300) {
    let message = `HTTP ${status}`
    if (res.data instanceof Blob) {
      const text = await res.data.text().catch(() => '')
      // Spring Boot 默认错误 JSON 一般包含 "error"/"message"/"path" 等字段。
      try {
        const json = JSON.parse(text)
        message =
          json.message || json.error || `${message}${json.path ? ` ${json.path}` : ''}` || text || message
      } catch {
        message = text || message
      }
    }
    throw new Error(message)
  }

  // 如果后端返回的是 JSON（路由/鉴权异常等），就不要触发一个“伪下载”。
  if (contentType.includes('application/json') && res.data instanceof Blob) {
    const text = await res.data.text().catch(() => '')
    throw new Error(text || '下载失败')
  }

  const blob = new Blob([res.data], { type: contentType || 'application/octet-stream' })
  const a = document.createElement('a')
  const blobUrl = URL.createObjectURL(blob)
  a.href = blobUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  // 过早 revoke 可能导致部分浏览器下载栏显示“失败”；延迟 revoke，确保下载已开始。
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 30_000)
  a.remove()
}

async function uploadExcel(url: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post<ImportResult>(url, form, { headers: { 'Content-Type': 'multipart/form-data' } })
  return res.data
}

export const baseExcel = {
  exportProducts(keyword?: string) {
    return downloadBlob('/api/base/products/export', 'products.xlsx', { keyword })
  },
  productTemplate() {
    return downloadBlob('/api/base/products/import-template', 'products-import-template.xlsx')
  },
  importProducts(file: File) {
    return uploadExcel('/api/base/products/import', file)
  },

  exportWarehouses(keyword?: string) {
    return downloadBlob('/api/base/warehouses/export', 'warehouses.xlsx', { keyword })
  },
  warehouseTemplate() {
    return downloadBlob('/api/base/warehouses/import-template', 'warehouses-import-template.xlsx')
  },
  importWarehouses(file: File) {
    return uploadExcel('/api/base/warehouses/import', file)
  },

  exportPartners(keyword?: string) {
    return downloadBlob('/api/base/partners/export', 'partners.xlsx', { keyword })
  },
  partnerTemplate() {
    return downloadBlob('/api/base/partners/import-template', 'partners-import-template.xlsx')
  },
  importPartners(file: File) {
    return uploadExcel('/api/base/partners/import', file)
  },

  exportCategories(keyword?: string) {
    return downloadBlob('/api/base/categories/export', 'categories.xlsx', { keyword })
  },
  categoryTemplate() {
    return downloadBlob('/api/base/categories/import-template', 'categories-import-template.xlsx')
  },
  importCategories(file: File) {
    return uploadExcel('/api/base/categories/import', file)
  }
}
