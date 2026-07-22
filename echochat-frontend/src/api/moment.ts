import client from './client'
import type { Result } from '@/types/api'
import type { MomentInfo, MomentLike, MomentComment, FeedItem } from '@/types/moment'

export async function getFeed(beforeId?: string, limit = 20): Promise<FeedItem[]> {
  const params: Record<string, string | number> = { limit }
  if (beforeId) params.beforeId = beforeId
  const res = await client.get<never, { data: Result<FeedItem[]> }>('/moment/feed', { params })
  return res.data.data
}

export async function publishMoment(body: {
  content: string
  media?: string
  visibility?: string
  showRange?: string
  blockUids?: string[]
}): Promise<MomentInfo> {
  const res = await client.post<never, { data: Result<MomentInfo> }>('/moment', body)
  return res.data.data
}

export async function deleteMoment(momentId: string): Promise<void> {
  await client.delete(`/moment/${momentId}`)
}

export async function likeMoment(momentId: string): Promise<void> {
  await client.post(`/moment/${momentId}/like`)
}

export async function unlikeMoment(momentId: string): Promise<void> {
  await client.delete(`/moment/${momentId}/like`)
}

export async function getLikes(momentId: string): Promise<MomentLike[]> {
  const res = await client.get<never, { data: Result<MomentLike[]> }>(`/moment/${momentId}/likes`)
  return res.data.data
}

export async function getComments(momentId: string): Promise<MomentComment[]> {
  const res = await client.get<never, { data: Result<MomentComment[]> }>(`/moment/${momentId}/comments`)
  return res.data.data
}

export async function addComment(momentId: string, body: {
  content: string
  replyToUid?: string
}): Promise<MomentComment> {
  const res = await client.post<never, { data: Result<MomentComment> }>(`/moment/${momentId}/comment`, body)
  return res.data.data
}

export async function deleteComment(commentId: number): Promise<void> {
  await client.delete(`/moment/comment/${commentId}`)
}

export async function getUserMoments(targetUid: string, beforeId?: string, limit = 20): Promise<MomentInfo[]> {
  const params: Record<string, string | number> = { limit }
  if (beforeId) params.beforeId = beforeId
  const res = await client.get<never, { data: Result<MomentInfo[]> }>(`/moment/user/${targetUid}`, { params })
  return res.data.data
}

export async function getMomentDetail(momentId: string): Promise<MomentInfo> {
  const res = await client.get<never, { data: Result<MomentInfo> }>(`/moment/${momentId}`)
  return res.data.data
}
