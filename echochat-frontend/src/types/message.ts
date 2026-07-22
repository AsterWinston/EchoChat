import type { MessageStatus, MessageType, SessionType } from './api'

export interface Message {
  msgId: string | number
  sessionType: SessionType
  fromUid: string | number
  toId: string
  msgType: MessageType
  content: string
  status: MessageStatus
  isRecalled: number
  isForwarded: number
  forwardFromUid: string | number | null
  replyToMsgId: string | number | null
  mentionedUids: string | null
  seq: number
  createdAt: string
  _clientId?: string
  _localSent?: boolean
  _failed?: boolean
  _uploadProgress?: number
  _uploading?: boolean
}
