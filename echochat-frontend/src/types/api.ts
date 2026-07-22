export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}


export interface PageResult<T> {
  page: number
  size: number
  total: number
  pages: number
  records: T[]
}


export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  uid: number
  nickname: string
}


export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'VOICE' | 'VIDEO' | 'SYSTEM'


export type SessionType = 'single' | 'group'


export type MessageStatus = 0 | 1 | 2


export type UserStatus = 0 | 1


export type Gender = 0 | 1 | 2


export type GroupRole = 'owner' | 'admin' | 'member'


export type MomentVisibility = 'public' | 'restricted'


export type NotificationType = 'friend_request' | 'group_invite' | 'system' | 'group_join_request' | 'group_join_approved'
