import type { SessionType } from './api'

export interface ConversationInfo {
  targetId: string
  sessionType: SessionType
  lastMsgId: string | null
  unreadCount: number
  isPinned: number
  updatedAt: string
  lastMsg: string | null
  lastMsgTime: string | null
  nickname: string
  avatar: string
  memo?: string
  online?: boolean
  dnd?: boolean
}
