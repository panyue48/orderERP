import { http } from './http'

async function downloadBlob(url: string, filename: string, params?: any) {
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
        message = json.message || json.error || `${message}${json.path ? ` ${json.path}` : ''}` || text || message
      } catch {
        message = text || message
      }
    }
    throw new Error(message)
  }

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
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 30_000)
  a.remove()
}

export const wmsExcel = {
  exportStocks(params?: { keyword?: string; warehouseId?: number }) {
    return downloadBlob('/api/wms/stocks/export', 'wms-stocks.xlsx', params)
  },
  exportStockLogs(params?: { keyword?: string; warehouseId?: number; productId?: number }) {
    return downloadBlob('/api/wms/stock-logs/export', 'wms-stock-logs.xlsx', params)
  }
}

