import { useState, useEffect, useCallback } from 'react'
import { getFriendList, sendFriendRequest } from '@/api/friend'
import { useChatStore } from '@/stores/chatStore'
import { listCache } from '@/stores/listCache'
import { useFriendRequestStore } from '@/stores/friendRequestStore'
import { useSearchQuery } from '@/components/chat/ChatSidebar'
import { useNotificationStore } from '@/stores/notificationStore'
import type { FriendInfo } from '@/types/friend'
import Avatar from '@/components/ui/Avatar'
import SendRequestModal from '@/components/chat/SendRequestModal'
import FriendRequestsModal from '@/components/chat/FriendRequestsPanel'

export default function ContactList({ onSelect }: { onSelect?: (uid: string) => void }) {
  const [friends, setFriends] = useState<FriendInfo[]>(listCache.friends ?? [])
  const [loading, setLoading] = useState(listCache.friends === null)
  const [error, setError] = useState('')
  const [fetchTrigger, setFetchTrigger] = useState(0)
  const searchQuery = useSearchQuery()
  const [showSendModal, setShowSendModal] = useState(false)
  const [showRequestsModal, setShowRequestsModal] = useState(false)
  const pendingCount = useFriendRequestStore((s) => s.pendingCount)
  const refreshFriendCount = useFriendRequestStore((s) => s.refresh)
  const notificationUnread = useNotificationStore((s) => s.unreadCount)

  const syncOnline = useCallback((list: FriendInfo[]) => {
    const statuses: Record<string, boolean> = {}
    const profiles: Record<string, { nickname: string; avatar?: string; memo?: string }> = {}
    for (const f of list) {
      statuses[f.uid] = f.status === 1
      profiles[f.uid] = { nickname: f.nickname, avatar: f.avatar, memo: f.memo }
    }
    useChatStore.getState().updateOnlineStatus(statuses)
    useChatStore.getState().updateUserProfiles(profiles)
  }, [])

  useEffect(() => {
    let cancelled = false
    if (listCache.friends === null) setLoading(true)
    setError('')

    getFriendList()
      .then((data) => {
        if (cancelled) return
        listCache.friends = data ?? []
        setFriends(listCache.friends)
        syncOnline(listCache.friends)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Failed to load')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [fetchTrigger])

  useEffect(() => { refreshFriendCount() }, [notificationUnread, refreshFriendCount, showRequestsModal])

  useEffect(() => {
    const interval = setInterval(() => {
      getFriendList()
        .then((data) => {
          if (data) {
            listCache.friends = data
            setFriends(data)
            syncOnline(data)
          }
        })
        .catch(() => {  })
      refreshFriendCount()
    }, 10000)
    return () => clearInterval(interval)
  }, [refreshFriendCount])

  const retry = useCallback(() => {
    listCache.friends = null
    setFetchTrigger((t) => t + 1)
  }, [])

  const handleSendRequest = useCallback(async (uid: string, message: string) => {
    await sendFriendRequest(uid, message)
  }, [])

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center py-12">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center py-12">
        <div className="text-center text-text-secondary">
          <p className="text-sm text-red-500 mb-2">{error}</p>
          <button onClick={retry} className="text-xs text-primary-500 hover:text-primary-400 cursor-pointer">
            Retry
          </button>
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-4 pt-4 pb-3 shrink-0">
          <span className="text-[13px] font-medium text-text-primary">Contacts</span>
          <button
            onClick={() => setShowSendModal(true)}
            className="flex items-center gap-1 text-xs font-medium text-primary-500 hover:text-primary-400 bg-primary-50 hover:bg-primary-100 rounded-lg px-2.5 py-1.5 transition-colors cursor-pointer"
          >
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
              <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Add Friend
          </button>
        </div>

        <button
            onClick={() => setShowRequestsModal(true)}
            className="w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-hover active:bg-surface-active transition-colors duration-150 cursor-pointer text-left border-b border-border-light"
          >
            <div className="w-12 h-12 rounded-full bg-primary-100 flex items-center justify-center shrink-0">
              <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="8.5" cy="7" r="4" /><line x1="20" y1="8" x2="20" y2="14" /><line x1="23" y1="11" x2="17" y2="11" />
              </svg>
            </div>
            <div className="flex-1 min-w-0">
              <span className="text-sm font-medium text-text-primary">Friend Requests</span>
            </div>
            {pendingCount > 0 && (
              <span className="min-w-[18px] h-[18px] px-1 flex items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
                {pendingCount > 99 ? '99+' : pendingCount}
              </span>
            )}
          </button>

        {friends.length === 0 && pendingCount === 0 ? (
          <div className="flex-1 flex items-center justify-center py-12">
            <div className="text-center text-text-secondary">
              <svg className="w-12 h-12 mx-auto mb-3 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
              </svg>
              <p className="text-sm text-text-secondary">No contacts yet</p>
              <p className="text-xs mt-1">Search users to add friends</p>
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto">
            {friends.filter((f) => !searchQuery || (f.memo || f.nickname)?.toLowerCase().includes(searchQuery)).map((friend) => (
              <button
                key={friend.uid}
                onClick={() => onSelect?.(friend.uid)}
                className="w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-hover active:bg-surface-active transition-colors duration-150 cursor-pointer text-left"
              >
                <Avatar
                  src={friend.avatar}
                  fallback={friend.memo || friend.nickname}
                  size="lg"
                  status={friend.status === 1 ? 'online' : 'offline'}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-text-primary truncate">
                      {friend.memo || friend.nickname}
                    </span>
                    {friend.groupName && (
                      <span className="text-[10px] text-text-secondary bg-surface-active rounded-md px-1.5 py-0.5 shrink-0">
                        {friend.groupName}
                      </span>
                    )}
                    {friend.memo && (
                      <span className="text-[10px] text-text-secondary/50 truncate max-w-[80px]">
                        ({friend.nickname})
                      </span>
                    )}
                  </div>
                  {friend.signature && (
                    <p className="text-xs text-text-secondary truncate mt-0.5">
                      {friend.signature}
                    </p>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
      <SendRequestModal open={showSendModal} onClose={() => setShowSendModal(false)} onSend={handleSendRequest} />
      <FriendRequestsModal open={showRequestsModal} onClose={() => setShowRequestsModal(false)} />
    </>
  )
}
