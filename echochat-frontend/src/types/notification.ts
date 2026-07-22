export interface Notification {
  id: string | number
  uid: string | number
  type: string
  title: string
  content: string | null
  relatedId: string | number | null
  isRead: number
  createdAt: string
}
