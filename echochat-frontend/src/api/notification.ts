import client from './client'
import type { Result } from '@/types/api'
import type { Notification } from '@/types/notification'

export async function getNotifications(limit = 50): Promise<Notification[]> {
  const res = await client.get<never, { data: Result<Notification[]> }>('/notification/list', { params: { limit } })
  return res.data.data
}

export async function getUnreadCount(): Promise<number> {
  const res = await client.get<never, { data: Result<{ count: number }> }>('/notification/unread-count')
  return res.data.data?.count ?? 0
}

export async function markAsRead(id: string): Promise<void> {
  await client.put(`/notification/read/${id}`)
}

export async function markAllRead(): Promise<void> {
  await client.put('/notification/read-all')
}
