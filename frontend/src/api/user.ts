import { http } from './http'

export type UserProfile = {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  avatar: string | null
}

export async function getMyProfile() {
  const res = await http.get<UserProfile>('/api/system/user/profile')
  return res.data
}

export async function updateMyProfile(payload: {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}) {
  const res = await http.put<UserProfile>('/api/system/user/profile', payload)
  return res.data
}

export async function changeMyPassword(payload: { oldPassword: string; newPassword: string }) {
  await http.post('/api/system/user/profile/password', payload)
}

export async function getMyPerms() {
  const res = await http.get<string[]>('/api/system/user/perms')
  return res.data
}
