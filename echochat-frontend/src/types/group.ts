import type { GroupRole } from './api'


export interface GroupInfo {
  gid: string
  name: string
  avatar: string
  ownerUid: string
  announcement: string
  slowModeInterval: number
  createdAt: string
  updatedAt: string
}


export interface GroupMember {
  id: number
  gid: string
  uid: string
  role: GroupRole
  muteUntil: string | null
  joinedAt: string
  nickname?: string
  avatar?: string
}


export interface GroupInvite {
  id: number
  gid: string
  code: string
  expireAt: string
  used: number
  createdAt: string
}
