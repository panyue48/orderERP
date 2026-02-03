import axios from 'axios'
import { getAuthToken } from '../auth/session'

export const http = axios.create({
  baseURL: '/',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem('token')
    }
    return Promise.reject(error)
  }
)
