import { http } from './http'

export type LoginResponse = {
  token: string
  user: {
    id: number
    username: string
    nickname: string | null
  }
}

export async function login(payload: { username: string; password: string }) {
  const res = await http.post<LoginResponse>('/api/auth/login', payload)
  return res.data
}

export type MenuMeta = {
  title: string
  icon?: string | null
}

export type MenuRoute = {
  name: string
  path: string
  component?: string | null
  meta: MenuMeta
  children?: MenuRoute[]
}

export async function fetchMenus() {
  const res = await http.get<MenuRoute[]>('/api/system/menu/routers')
  return res.data
}
