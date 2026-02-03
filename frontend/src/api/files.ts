import { http } from './http'

export type UploadResponse = {
  url: string
  filename: string
  size: number
}

export async function uploadImage(file: File) {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post<UploadResponse>('/api/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

