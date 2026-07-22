export interface FriendRequest {
  id: string | number
  fromUid: string | number
  toUid: string | number
  message: string
  status: 'pending' | 'accepted' | 'rejected' | 'expired'
  expireAt: string
  createdAt: string
  handledAt: string | null
}


export interface FriendInfo {
  uid: string
  nickname: string
  avatar: string
  groupName: string
  memo?: string
  signature?: string
  status?: number
  lastSeen?: string
  gender?: number
  createdAt?: string
}
