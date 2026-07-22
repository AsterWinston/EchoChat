import { create } from 'zustand'
import type { Notification } from '@/types/notification'
import * as notificationApi from '@/api/notification'

interface NotificationState {
  notifications: Notification[]
  unreadCount: number
  loading: boolean

  loadNotifications: () => Promise<void>
  markRead: (id: string) => Promise<void>
  markAllRead: () => Promise<void>
  appendNotification: (notification: Notification) => void
  setUnreadCount: (count: number) => void
  reset: () => void
}



export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  loading: false,

  loadNotifications: async () => {
    set({ loading: true })
    try {
      const data = await notificationApi.getNotifications()
      set({
        notifications: data ?? [],
        unreadCount: (data ?? []).filter((n) => n.isRead === 0).length,
        loading: false,
      })
    } catch {
      set({ loading: false })
    }
  },

  markRead: async (id) => {
    const prev = get().notifications.find((n) => String(n.id) === id)
    set((s) => ({
      notifications: s.notifications.map((n) => (String(n.id) === id ? { ...n, isRead: 1 } : n)),
      unreadCount: Math.max(0, s.unreadCount - 1),
    }))
    try {
      await notificationApi.markAsRead(id)
    } catch {
      set((s) => ({
        notifications: s.notifications.map((n) =>
          String(n.id) === id && prev ? { ...n, isRead: prev.isRead } : n,
        ),
        unreadCount: prev?.isRead === 0 ? s.unreadCount + 1 : s.unreadCount,
      }))
    }
  },

  markAllRead: async () => {
    const prev = get().notifications
    set((s) => ({
      notifications: s.notifications.map((n) => ({ ...n, isRead: 1 })),
      unreadCount: 0,
    }))
    try {
      await notificationApi.markAllRead()
    } catch {
      set({ notifications: prev, unreadCount: prev.filter((n) => n.isRead === 0).length })
    }
  },

  appendNotification: (notification) => {
    set((s) => ({
      notifications: [notification, ...s.notifications],
      unreadCount: s.unreadCount + (notification.isRead === 0 ? 1 : 0),
    }))
  },

  setUnreadCount: (count) => {
    set({ unreadCount: count })
  },

  reset: () => {
    set({ notifications: [], unreadCount: 0, loading: false })
  },
}))
