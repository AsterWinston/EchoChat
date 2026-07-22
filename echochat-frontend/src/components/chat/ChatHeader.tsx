import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { getGroupMembers } from '@/api/group'
import { getBatchOnlineStatus } from '@/api/user'
import type { GroupMember } from '@/types/group'
import Avatar from '@/components/ui/Avatar'
import type { ConversationInfo } from '@/types/conversation'

interface ChatHeaderProps {
  conv: ConversationInfo
  onAvatarClick?: () => void
  searchOpen?: boolean
  onToggleSearch?: () => void
}

export default function ChatHeader({ conv, onAvatarClick, searchOpen, onToggleSearch }: ChatHeaderProps) {
  const togglePin = useChatStore((s) => s.togglePin)
  const onlineStatus = useChatStore((s) => s.onlineStatus)
  const conversations = useChatStore((s) => s.conversations)
  const typingUsers = useChatStore((s) => s.typingUsers)
  const myUid = useAuthStore((s) => s.user?.uid)
  const navigate = useNavigate()
  const isGroup = conv.sessionType === 'group'
  const isPinned = conv.isPinned === 1
  const displayName = conv.memo || conv.nickname
  const [members, setMembers] = useState<GroupMember[]>([])
  const [groupOnline, setGroupOnline] = useState<Record<string, boolean>>({})

  const lastTyping = !isGroup ? typingUsers[conv.targetId] : undefined
  const isTyping = lastTyping != null && Date.now() - lastTyping < 6000
  const [, setTick] = useState(0)

  useEffect(() => {
    if (!lastTyping) return
    const remaining = 6000 - (Date.now() - lastTyping)
    if (remaining <= 0) return
    const t = setTimeout(() => setTick((n) => n + 1), remaining + 100)
    return () => clearTimeout(t)
  }, [lastTyping])

  useEffect(() => {
    if (!isGroup) return

    let active = true
    const refreshStatus = () => {
      getGroupMembers(conv.targetId)
        .then(async (m) => {
          if (!active) return
          setMembers(m)
          const store = useChatStore.getState()
          const allUids = m
            .map((mb) => String(mb.uid))
            .filter((uid) => uid !== myUid && !store.conversations.some((c) => c.targetId === uid && c.sessionType === 'single'))
          if (allUids.length > 0) {
            const statuses = await getBatchOnlineStatus(allUids).catch(() => ({} as Record<string, boolean>))
            if (!active) return
            const filtered: Record<string, boolean> = {}
            for (const [k, v] of Object.entries(statuses)) {
              filtered[k] = v === true
            }
            useChatStore.getState().updateOnlineStatus(filtered)
            setGroupOnline((prev) => ({ ...prev, ...filtered }))
          }
        })
        .catch(() => {
          if (active) setMembers([])
        })
      useChatStore.getState().refreshConversations()
    }

    refreshStatus()
    const timer = setInterval(refreshStatus, 5000)
    return () => { active = false; clearInterval(timer) }
  }, [conv.targetId, isGroup, myUid])

  const mergedOnline = useMemo(() => ({ ...onlineStatus, ...groupOnline }), [onlineStatus, groupOnline])

  const { total, onlineCount } = useMemo(() => {
    const others = members.filter((m) => String(m.uid) !== myUid)
    const t = others.length
    const o = others.filter((m) => {
      const uid = String(m.uid)
      const conv = conversations.find((c) => c.targetId === uid && c.sessionType === 'single')
      if (conv?.online !== undefined) return conv.online
      return mergedOnline[uid] === true
    }).length
    return { total: t, onlineCount: o }
  }, [members, mergedOnline, conversations, myUid])

  return (
    <div className="flex items-center gap-3 px-5 py-3 bg-surface border-b border-border-light shrink-0 select-none">
      {isGroup ? (
        <div
          className="shrink-0 cursor-pointer"
          onClick={() => navigate(`/group/${conv.targetId}/profile`)}
        >
          <Avatar
            src={conv.avatar}
            fallback={displayName}
            size="lg"
          />
        </div>
      ) : (
        <div className="shrink-0 cursor-pointer" onClick={onAvatarClick}>
          <Avatar
            src={conv.avatar}
            fallback={displayName}
            size="lg"
            status={onlineStatus[conv.targetId] ? 'online' : offlineStatus(conv)}
          />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <h2
            className={`text-base font-semibold text-text-primary truncate ${isGroup ? 'cursor-pointer hover:text-primary-500' : ''}`}
            onClick={isGroup ? () => navigate(`/group/${conv.targetId}/profile`) : undefined}
          >
            {displayName}
          </h2>
          {isPinned && (
            <svg className="w-3 h-3 text-primary-500 shrink-0" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="17" x2="12" y2="22" />
              <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z" />
            </svg>
          )}
        </div>
        <p className="text-xs text-text-secondary mt-0.5">
          {isGroup
            ? `${onlineCount}/${total} online`
            : isTyping
              ? 'typing...'
              : onlineStatus[conv.targetId]
                ? 'Online'
                : offlineStatus(conv) === 'offline' ? 'Offline' : ''
          }
        </p>
      </div>
      {!isGroup && (
        <button
          onClick={onToggleSearch}
          className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
            searchOpen ? 'text-primary-500 bg-primary-50' : 'text-text-secondary hover:text-primary-500 hover:bg-primary-50'
          }`}
          title="Search in conversation"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
          </svg>
        </button>
      )}
      <button
        onClick={() => togglePin(conv.targetId, conv.sessionType)}
        className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
          isPinned ? 'text-primary-500 bg-primary-50' : 'text-text-secondary hover:text-primary-500 hover:bg-primary-50'
        }`}
        title={isPinned ? 'Unpin' : 'Pin'}
      >
        <svg className="w-4 h-4" viewBox="0 0 24 24" fill={isPinned ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <line x1="12" y1="17" x2="12" y2="22" />
          <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z" />
        </svg>
      </button>
    </div>
  )
}

function offlineStatus(conv: ConversationInfo): 'offline' | undefined {
  return conv.online === false ? 'offline' as const : undefined
}
