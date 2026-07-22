import type { MomentVisibility } from './api'

export interface MomentInfo {
  momentId: string
  uid: string
  content: string
  media?: string
  visibility: MomentVisibility
  showRange: string | null
  createdAt: string
  updatedAt: string
  likeCount?: number
  commentCount?: number
  isLiked?: boolean
}

export interface MomentLike {
  id: number
  momentId: string
  uid: string
  createdAt: string
}

export interface MomentComment {
  id: number
  momentId: string
  uid: string
  replyToUid: string | null
  content: string
  createdAt: string
}

export interface FeedItem {
  momentId: string
  uid: string
  content: string
  media?: string
  visibility: MomentVisibility
  showRange: string | null
  createdAt: string
  likeCount: number
  commentCount: number
  isLiked?: boolean
}
