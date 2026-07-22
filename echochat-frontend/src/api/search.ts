import client from './client'
import type { Result } from '@/types/api'
import type { MessageSearchResult, UserSearchResult, GroupSearchResult } from '@/types/search'

export async function searchMessages(q: string): Promise<MessageSearchResult[]> {
  const res = await client.get<never, { data: Result<MessageSearchResult[]> }>('/search/messages', { params: { q } })
  return res.data.data
}

export async function searchUsers(q: string): Promise<UserSearchResult[]> {
  const res = await client.get<never, { data: Result<UserSearchResult[]> }>('/search/users', { params: { q } })
  return res.data.data
}

export async function searchGroups(q: string): Promise<GroupSearchResult[]> {
  const res = await client.get<never, { data: Result<GroupSearchResult[]> }>('/search/groups', { params: { q } })
  return res.data.data
}

export async function searchMessagesWithUser(q: string, targetUid: string): Promise<MessageSearchResult[]> {
  const res = await client.get<never, { data: Result<MessageSearchResult[]> }>(`/search/messages/with/${targetUid}`, { params: { q } })
  return res.data.data
}
