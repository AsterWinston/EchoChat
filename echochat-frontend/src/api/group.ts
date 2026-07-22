import client from './client'
import type { Result } from '@/types/api'
import type { GroupInfo, GroupMember, GroupInvite } from '@/types/group'

export async function createGroup(name: string): Promise<GroupInfo> {
  const res = await client.post<never, { data: Result<GroupInfo> }>('/group', { name })
  return res.data.data
}

export async function getJoinedGroups(): Promise<GroupInfo[]> {
  const res = await client.get<never, { data: Result<GroupInfo[]> }>('/group/joined')
  return res.data.data
}

export async function getOwnedGroups(): Promise<GroupInfo[]> {
  const res = await client.get<never, { data: Result<GroupInfo[]> }>('/group/owned')
  return res.data.data
}

export async function getGroupInfo(gid: string): Promise<GroupInfo> {
  const res = await client.get<never, { data: Result<GroupInfo> }>(`/group/${gid}`)
  return res.data.data
}

export async function getGroupMembers(gid: string): Promise<GroupMember[]> {
  const res = await client.get<never, { data: Result<GroupMember[]> }>(`/group/${gid}/members`)
  return res.data.data
}

export async function updateGroup(gid: string, data: Partial<GroupInfo>): Promise<GroupInfo> {
  const res = await client.put<never, { data: Result<GroupInfo> }>(`/group/${gid}`, data)
  return res.data.data
}

export async function leaveGroup(gid: string): Promise<void> {
  await client.delete(`/group/${gid}/leave`)
}

export async function dissolveGroup(gid: string): Promise<void> {
  await client.delete(`/group/${gid}`)
}

export async function transferOwner(gid: string, newOwnerUid: string): Promise<void> {
  await client.put(`/group/${gid}/transfer`, { newOwnerUid })
}

export async function kickMember(gid: string, uid: string): Promise<void> {
  await client.delete(`/group/${gid}/members/${uid}`)
}

export async function setRole(gid: string, uid: string, role: string): Promise<void> {
  await client.put(`/group/${gid}/members/${uid}/role`, { role })
}

export async function muteMember(gid: string, uid: string, minutes: number): Promise<void> {
  await client.post(`/group/${gid}/members/${uid}/mute`, { minutes })
}

export async function unmuteMember(gid: string, uid: string): Promise<void> {
  await client.delete(`/group/${gid}/members/${uid}/mute`)
}

export async function createInvite(gid: string, expireHours: number): Promise<GroupInvite> {
  const res = await client.post<never, { data: Result<GroupInvite> }>(`/group/${gid}/invite`, { expireHours })
  return res.data.data
}

export interface InviteInfo {
  gid: string
  groupName: string
  code: string
  expireAt: string
  expired: boolean
}

export async function getInviteInfo(code: string): Promise<InviteInfo> {
  const res = await client.get<never, { data: Result<InviteInfo> }>(`/group/invite/${code}`)
  return res.data.data
}

export async function joinByInvite(code: string): Promise<void> {
  await client.post(`/group/join/${code}`)
}

export async function inviteMembers(gid: string, uids: string[]): Promise<void> {
  await client.post(`/group/${gid}/members`, { uids })
}

export interface GroupJoinRequest {
  id: string
  gid: string
  uid: string
  status: 'pending' | 'approved' | 'rejected'
  processedBy: string | null
  message: string | null
  createdAt: string
  processedAt: string | null
}

export async function applyToJoinGroup(gid: string, message?: string): Promise<GroupJoinRequest> {
  const res = await client.post<never, { data: Result<GroupJoinRequest> }>(`/group/${gid}/join-request`, { message: message || '' })
  return res.data.data
}

export async function getPendingJoinRequests(gid: string): Promise<GroupJoinRequest[]> {
  const res = await client.get<never, { data: Result<GroupJoinRequest[]> }>(`/group/${gid}/join-requests`)
  return res.data.data
}

export async function approveJoinRequest(id: string): Promise<GroupJoinRequest> {
  const res = await client.put<never, { data: Result<GroupJoinRequest> }>(`/group/join-requests/${id}/approve`)
  return res.data.data
}

export async function rejectJoinRequest(id: string): Promise<GroupJoinRequest> {
  const res = await client.put<never, { data: Result<GroupJoinRequest> }>(`/group/join-requests/${id}/reject`)
  return res.data.data
}

export async function getMyJoinRequest(gid: string): Promise<GroupJoinRequest | null> {
  const res = await client.get<never, { data: Result<GroupJoinRequest> }>(`/group/${gid}/join-request/my`)
  return res.data.data
}

export async function getGroupInfoPublic(gid: string): Promise<GroupInfo & { isMember: boolean; memberRole: string | null }> {
  const res = await client.get<never, { data: Result<GroupInfo & { isMember: boolean; memberRole: string | null }> }>(`/group/${gid}/check`)
  return res.data.data
}

export async function muteAll(gid: string): Promise<void> {
  await client.put(`/group/${gid}/mute-all`)
}

export async function unmuteAll(gid: string): Promise<void> {
  await client.delete(`/group/${gid}/mute-all`)
}
