import { http } from './http'

export type ImportResult = {
  total: number
  inserted: number
  updated: number
  failed: number
  errors: { rowNum: number; message: string }[]
}

async function downloadBlob(url: string, filename: string, params?: any) {
  // We intentionally handle non-2xx here so we can decode JSON error responses (which come back as Blob).
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
      // Spring Boot default error JSON contains "error"/"message"/"path" fields.
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

  // If backend returns JSON (misconfigured route or auth), avoid triggering a bogus download.
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
  // Revoke too early can cause some browsers to show "failed" in the download bar.
  // Delay revocation to ensure the download has started.
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
