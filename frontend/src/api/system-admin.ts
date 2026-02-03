import { http } from './http'

export type AdminUser = {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  avatar: string | null
  status: number | null
  roleIds: number[]
  roleKeys: string[]
}

export type AdminRole = {
  id: number
  roleName: string
  roleKey: string
  sort: number | null
  status: number | null
}

export type AdminMenu = {
  id: number
  parentId: number | null
  menuName: string
  path: string | null
  component: string | null
  perms: string | null
  icon: string | null
  menuType: string | null
  sort: number | null
  visible: number | null
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export async function listUsers(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<AdminUser>>('/api/system/users', { params })
  return res.data
}

export async function createUser(payload: {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status?: number
  roleIds?: number[]
}) {
  const res = await http.post<AdminUser>('/api/system/users', payload)
  return res.data
}

export async function updateUser(
  id: number,
  payload: {
    nickname?: string
    email?: string
    phone?: string
    avatar?: string
    status?: number
    roleIds?: number[]
  }
) {
  const res = await http.put<AdminUser>(`/api/system/users/${id}`, payload)
  return res.data
}

export async function deleteUser(id: number) {
  await http.delete(`/api/system/users/${id}`)
}

export async function listRoles(params: { page: number; size: number; keyword?: string }) {
  const res = await http.get<PageResponse<AdminRole>>('/api/system/roles', { params })
  return res.data
}

export async function createRole(payload: { roleName: string; roleKey: string; sort?: number; status?: number }) {
  const res = await http.post<AdminRole>('/api/system/roles', payload)
  return res.data
}

export async function updateRole(
  id: number,
  payload: { roleName?: string; roleKey?: string; sort?: number; status?: number }
) {
  const res = await http.put<AdminRole>(`/api/system/roles/${id}`, payload)
  return res.data
}

export async function deleteRole(id: number) {
  await http.delete(`/api/system/roles/${id}`)
}

export async function listMenus() {
  const res = await http.get<AdminMenu[]>('/api/system/menus')
  return res.data
}

export async function getRoleMenus(roleId: number) {
  const res = await http.get<number[]>(`/api/system/roles/${roleId}/menus`)
  return res.data
}

export async function updateRoleMenus(roleId: number, menuIds: number[]) {
  await http.put(`/api/system/roles/${roleId}/menus`, { menuIds })
}
