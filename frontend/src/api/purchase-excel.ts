import { http } from './http'
import type { ImportResult } from './base-excel'

async function downloadBlob(url: string, filename: string, params?: any) {
  // 故意不让 axios 直接抛非 2xx：这样才能读取 Blob 并解析后端返回的 JSON 错误信息。
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

  // 如果后端返回的是 JSON（路由/鉴权异常等），就不要触发一个“假下载”。
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

export const purchaseExcel = {
  exportOrders(keyword?: string) {
    return downloadBlob('/api/purchase/orders/export', 'purchase-orders.xlsx', { keyword })
  },
  orderTemplate() {
    return downloadBlob('/api/purchase/orders/import-template', 'purchase-orders-import-template.xlsx')
  },
  importOrders(file: File) {
    return uploadExcel('/api/purchase/orders/import', file)
  }
}

