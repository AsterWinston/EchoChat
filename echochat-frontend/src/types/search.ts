export interface MessageSearchResult {
  msgId: string
  sessionType: string
  fromUid: string
  toId: string
  msgType: string
  content: string
  sessionId: string
  createdAt: string
  
  fromName?: string
  
  toName?: string
}


export interface UserSearchResult {
  uid: string
  nickname: string
  email: string
}


export interface GroupSearchResult {
  gid: string
  name: string
  ownerUid: string
  avatar: string
}
