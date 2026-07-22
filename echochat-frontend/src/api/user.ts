import client from './client'
import type { Result } from '@/types/api'
import type { User } from '@/types/user'

export async function getUserProfile(uid: string): Promise<User> {
  const res = await client.get<never, { data: Result<User> }>(`/user/profile/${uid}`)
  return res.data.data
}

export async function getOwnProfile(): Promise<User> {
  const res = await client.get<never, { data: Result<User> }>('/user/profile')
  return res.data.data
}

export async function updateProfile(fields: Partial<User>): Promise<User> {
  const res = await client.put<never, { data: Result<User> }>('/user/profile', fields)
  return res.data.data
}

export async function getBatchOnlineStatus(uids: string[]): Promise<Record<string, boolean>> {
  const res = await client.post<never, { data: Result<Record<string, boolean>> }>('/user/online-status/batch', uids)
  return res.data.data
}

export async function searchUsersByKeyword(keyword: string): Promise<User[]> {
  const res = await client.get<never, { data: Result<User[]> }>('/user/search', { params: { keyword } })
  return res.data.data
}
