import client from './client'
import type { Result } from '@/types/api'
import type { FriendInfo, FriendRequest } from '@/types/friend'

export interface BlacklistUser {
  uid: string
  nickname: string
  avatar: string
  createdAt: string
}

export async function getFriendList(): Promise<FriendInfo[]> {
  const res = await client.get<never, { data: Result<FriendInfo[]> }>('/friend/list')
  return res.data.data
}

export async function getReceivedRequests(status?: string): Promise<FriendRequest[]> {
  const params = status ? { status } : {}
  const res = await client.get<never, { data: Result<FriendRequest[]> }>('/friend/requests/received', { params })
  return res.data.data
}

export async function getSentRequests(status?: string): Promise<FriendRequest[]> {
  const params = status ? { status } : {}
  const res = await client.get<never, { data: Result<FriendRequest[]> }>('/friend/requests/sent', { params })
  return res.data.data
}

export async function sendFriendRequest(toUid: string, message?: string): Promise<void> {
  await client.post('/friend/request', { toUid, message })
}

export async function acceptFriendRequest(id: string): Promise<void> {
  await client.put(`/friend/request/${id}/accept`)
}

export async function rejectFriendRequest(id: string): Promise<void> {
  await client.put(`/friend/request/${id}/reject`)
}

export async function deleteFriend(uid: string): Promise<void> {
  await client.delete(`/friend/${uid}`)
}

export async function getBlacklist(): Promise<BlacklistUser[]> {
  const res = await client.get<never, { data: Result<BlacklistUser[]> }>('/friend/blacklist')
  return res.data.data
}

export async function blockUser(uid: string): Promise<void> {
  await client.post(`/friend/blacklist/${uid}`)
}

export async function unblockUser(uid: string): Promise<void> {
  await client.delete(`/friend/blacklist/${uid}`)
}

export async function updateFriend(friendUid: string, data: { groupName?: string; memo?: string }): Promise<void> {
  await client.put(`/friend/${friendUid}`, data)
}
