import { useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useNotificationStore } from '@/stores/notificationStore'
import { formatTime } from '@/utils/time'
import type { Notification } from '@/types/notification'

function getTypeIcon(type: string) {
  switch (type) {
    case 'friend_request':
      return (
        <div className="w-9 h-9 rounded-full bg-primary-100 flex items-center justify-center">
          <svg className="w-4 h-4 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M16 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" /><circle cx="8.5" cy="7" r="4" /><line x1="20" y1="8" x2="20" y2="14" /><line x1="23" y1="11" x2="17" y2="11" />
          </svg>
        </div>
      )
    case 'group_invite':
      return (
        <div className="w-9 h-9 rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
          <svg className="w-4 h-4 text-green-600 dark:text-green-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
          </svg>
        </div>
      )
    case 'group_join_request':
      return (
        <div className="w-9 h-9 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center">
          <svg className="w-4 h-4 text-blue-600 dark:text-blue-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M16 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" /><circle cx="8.5" cy="7" r="4" /><polyline points="17 11 19 13 23 9" />
          </svg>
        </div>
      )
    case 'group_join_approved':
      return (
        <div className="w-9 h-9 rounded-full bg-emerald-100 dark:bg-emerald-900 flex items-center justify-center">
          <svg className="w-4 h-4 text-emerald-600 dark:text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" /><circle cx="9" cy="7" r="4" /><polyline points="16 11 18 13 22 9" />
          </svg>
        </div>
      )
    case 'system':
      return (
        <div className="w-9 h-9 rounded-full bg-amber-100 dark:bg-amber-900 flex items-center justify-center">
          <svg className="w-4 h-4 text-amber-600 dark:text-amber-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
      )
    default:
      return (
        <div className="w-9 h-9 rounded-full bg-surface-active flex items-center justify-center">
          <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 01-3.46 0" />
          </svg>
        </div>
      )
  }
}

function getNotificationUrl(n: Notification): string | null {
  if (n.relatedId == null) return null
  switch (n.type) {
    case 'friend_request': return '/contacts'
    case 'group_invite': return `/chat/group/${n.relatedId}`
    case 'group_join_request': return `/group/${n.relatedId}/profile`
    case 'group_join_approved': return `/chat/group/${n.relatedId}`
    default: return null
  }
}



export default function NotificationPage() {
  const notifications = useNotificationStore((s) => s.notifications)
  const loading = useNotificationStore((s) => s.loading)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const loadNotifications = useNotificationStore((s) => s.loadNotifications)
  const markRead = useNotificationStore((s) => s.markRead)
  const markAllRead = useNotificationStore((s) => s.markAllRead)
  const navigate = useNavigate()

  useEffect(() => { loadNotifications() }, [loadNotifications])

  const handleClick = useCallback((n: Notification) => {
    if (n.isRead === 0) markRead(String(n.id))
    const url = getNotificationUrl(n)
    if (url) navigate(url)
  }, [markRead, navigate])

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="flex items-center justify-between px-6 py-4 bg-surface border-b border-border-light shrink-0">
        <div className="flex items-center gap-2">
          <h2 className="text-base font-semibold text-text-primary">Notifications</h2>
          {unreadCount > 0 && (
            <span className="min-w-[18px] h-[18px] px-1 flex items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <button
            onClick={() => markAllRead()}
            className="text-xs text-primary-500 hover:text-primary-400 transition-colors cursor-pointer"
          >
            Mark all read
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex justify-center py-16">
            <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        ) : notifications.length === 0 ? (
          <div className="flex items-center justify-center py-20">
            <div className="text-center text-text-secondary">
              <div className="w-16 h-16 rounded-2xl bg-surface-active flex items-center justify-center mx-auto mb-3">
                <svg className="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 01-3.46 0" />
                </svg>
              </div>
              <p className="text-sm">No notifications</p>
              <p className="text-xs mt-1">You're all caught up</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-border-light">
            {notifications.map((n) => (
              <button
                key={n.id}
                onClick={() => handleClick(n)}
                className={`w-full flex items-start gap-3 px-6 py-4 text-left transition-colors cursor-pointer hover:bg-surface-hover ${
                  n.isRead === 0 ? 'bg-primary-50/30 dark:bg-primary-900/10' : ''
                }`}
              >
                {getTypeIcon(n.type)}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`text-sm truncate ${n.isRead === 0 ? 'font-medium text-text-primary' : 'text-text-primary'}`}>
                      {n.title}
                    </span>
                    {n.isRead === 0 && (
                      <span className="w-2 h-2 rounded-full bg-primary-500 shrink-0" />
                    )}
                  </div>
                  {n.content && (
                    <p className="text-xs text-text-secondary mt-0.5 line-clamp-2">{n.content}</p>
                  )}
                  <p className="text-[10px] text-text-secondary/50 mt-1">{formatTime(n.createdAt)}</p>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
