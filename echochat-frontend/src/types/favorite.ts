export interface Favorite {
  id: number
  uid: number
  msgId: string | number
  msgType: string
  msgSummary: string
  content?: string | null
  fromUid?: string | null
  collectedAt: string
}
