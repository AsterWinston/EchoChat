import client from './client'
import type { Result } from '@/types/api'
import type { Message } from '@/types/message'
import type { ConversationInfo } from '@/types/conversation'

export async function getConversations(): Promise<ConversationInfo[]> {
  const res = await client.get<never, { data: Result<ConversationInfo[]> }>('/message/conversations')
  return res.data.data
}

export async function getHistory(targetUid: string, beforeSeq?: number, limit = 20): Promise<Message[]> {
  const params: Record<string, string | number> = { limit }
  if (beforeSeq) params.beforeSeq = beforeSeq
  const res = await client.get<never, { data: Result<Message[]> }>(`/message/history/${targetUid}`, { params })
  return res.data.data
}

export async function getGroupHistory(gid: string, beforeSeq?: number, limit = 20): Promise<Message[]> {
  const params: Record<string, string | number> = { limit }
  if (beforeSeq) params.beforeSeq = beforeSeq
  const res = await client.get<never, { data: Result<Message[]> }>(`/message/group/${gid}/history`, { params })
  return res.data.data
}

export async function sendMessage(toUid: string, content: string, msgType = 'TEXT'): Promise<Message> {
  const res = await client.post<never, { data: Result<Message> }>('/message/send', { toUid, msgType, content })
  return res.data.data
}

export async function sendGroupMessage(gid: string, content: string, msgType = 'TEXT', replyToMsgId?: string): Promise<Message> {
  const body: Record<string, unknown> = { gid, msgType, content }
  if (replyToMsgId) body.replyToMsgId = replyToMsgId
  const res = await client.post<never, { data: Result<Message> }>('/message/send/group', body)
  return res.data.data
}

export async function markAsRead(targetUid: string): Promise<void> {
  await client.put(`/message/read/${targetUid}`)
}

export async function markGroupAsRead(gid: string): Promise<void> {
  await client.put(`/message/group/${gid}/read`)
}

export async function recallMessage(msgId: string): Promise<Message> {
  const res = await client.put<never, { data: Result<Message> }>(`/message/recall/${msgId}`)
  return res.data.data
}

export async function deleteMessage(msgId: string): Promise<void> {
  await client.delete(`/message/${msgId}`)
}

export async function pinConversation(targetId: string, pinned: boolean): Promise<void> {
  await client.put(`/message/conversation/${targetId}/pin`, { pinned })
}

export async function pinGroupConversation(gid: string, pinned: boolean): Promise<void> {
  await client.put(`/message/group/${gid}/pin`, { pinned })
}

export async function replyMessage(toUid: string, replyToMsgId: string, content: string): Promise<Message> {
  const res = await client.post<never, { data: Result<Message> }>('/message/reply', { toUid, replyToMsgId, content })
  return res.data.data
}

export async function forwardMessage(toUid: string, msgId: string): Promise<Message> {
  const res = await client.post<never, { data: Result<Message> }>('/message/forward', { toUid, msgId })
  return res.data.data
}

export async function forwardGroupMessage(gid: string, msgId: string): Promise<Message> {
  const res = await client.post<never, { data: Result<Message> }>('/message/forward/group', { gid, msgId })
  return res.data.data
}

export async function deleteConversation(targetUid: string): Promise<void> {
  await client.delete(`/message/conversation/${targetUid}`)
}

export async function deleteGroupConversation(gid: string): Promise<void> {
  await client.delete(`/message/group/${gid}`)
}

export interface ReadReceipt {
  readCount: number
  readUids: string[]
}

export async function getReadReceipt(msgId: string): Promise<ReadReceipt> {
  const res = await client.get<never, { data: Result<ReadReceipt> }>(`/message/${msgId}/read/status`)
  return res.data.data
}

export interface PinnedMsg {
  msgId: string
  contentSummary: string
  pinnedBy: string
  pinnedAt: string
  content: string
  msgType: string
}

export async function pinMessage(msgId: string, targetUid: string, contentSummary?: string): Promise<void> {
  await client.put(`/message/pin/${msgId}`, { targetUid, contentSummary: contentSummary || '' })
}

export async function getPinnedMessages(sessionType: string, targetId: string): Promise<PinnedMsg[]> {
  const res = await client.get<never, { data: Result<PinnedMsg[]> }>(`/message/pinned/${sessionType}/${targetId}`)
  return res.data.data
}

export async function unpinMessage(msgId: string): Promise<void> {
  await client.delete(`/message/pin/${msgId}`)
}

export async function getMessageContext(msgId: string, size = 20): Promise<Message[]> {
  const res = await client.get<never, { data: Result<Message[]> }>(`/message/context/${msgId}`, { params: { size } })
  return res.data.data
}

export async function setConversationDnd(targetUid: string, enabled: boolean): Promise<void> {
  await client.put(`/message/conversation/${targetUid}/dnd`, { enabled })
}

export async function syncMessages(sessionType: string, targetId: string, afterSeq: number): Promise<Message[]> {
  const res = await client.get<never, { data: Result<Message[]> }>(`/message/sync/${sessionType}/${targetId}`, { params: { afterSeq } })
  return res.data.data
}
