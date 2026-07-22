import { memo, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChatStore } from '@/stores/chatStore'
import { useSearchQuery } from '@/components/chat/ChatSidebar'
import { formatTime } from '@/utils/time'
import Avatar from '@/components/ui/Avatar'
import ConfirmDialog from '@/components/ui/ConfirmDialog'

function formatLastMsg(content: string | null): string {
  if (!content) return 'No messages'
  if (content.startsWith('[')) {
    try {
      const arr = JSON.parse(content)
      if (arr.length > 0) return '[Image]'
    } catch {  }
  }
  if (content.startsWith('{')) {
    try {
      const obj = JSON.parse(content)
      if (obj.mimeType?.startsWith('video/')) return '[Video]'
      if (obj.mimeType?.startsWith('audio/')) return '[Voice]'
      return '[File]'
    } catch {  }
  }
  return content
}



export default function ConversationList() {
  const conversations = useChatStore((s) => s.conversations)
  const conversationsLoading = useChatStore((s) => s.conversationsLoading)
  const activeConversation = useChatStore((s) => s.activeConversation)
  const setActiveConversation = useChatStore((s) => s.setActiveConversation)
  const togglePin = useChatStore((s) => s.togglePin)
  const toggleDnd = useChatStore((s) => s.toggleDnd)
  const deleteConversation = useChatStore((s) => s.deleteConversation)
  const navigate = useNavigate()
  const searchQuery = useSearchQuery()

  const [deletingConv, setDeletingConv] = useState<{ targetId: string; sessionType: string } | null>(null)

  const handleDeleteClick = useCallback((targetId: string, sessionType: string, e: React.MouseEvent) => {
    e.stopPropagation()
    setDeletingConv({ targetId, sessionType })
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (!deletingConv) return
    const { targetId, sessionType } = deletingConv
    deleteConversation(targetId, sessionType)
    if (activeConversation?.targetId === targetId && activeConversation?.sessionType === sessionType) {
      navigate('/chat', { replace: true })
    }
    setDeletingConv(null)
  }, [deletingConv, deleteConversation, activeConversation, navigate])

  if (conversationsLoading) {
    return (
      <div className="flex-1 flex items-center justify-center py-12">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (conversations.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center text-text-secondary px-4">
          <div className="w-16 h-16 rounded-2xl bg-primary-100 flex items-center justify-center mx-auto mb-3">
            <svg className="w-8 h-8 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
            </svg>
          </div>
          <p className="text-sm">No conversations yet</p>
          <p className="text-xs mt-1">Start a chat with a contact</p>
        </div>
      </div>
    )
  }

  const pinnedConvs = conversations.filter((c) => c.isPinned === 1 && (!searchQuery || (c.memo || c.nickname)?.toLowerCase().includes(searchQuery)))
  const normalConvs = conversations.filter((c) => c.isPinned !== 1 && (!searchQuery || (c.memo || c.nickname)?.toLowerCase().includes(searchQuery)))

  const renderRow = (conv: typeof conversations[number]) => (
    <ConversationRow
      key={conv.targetId}
      conv={conv}
      isActive={activeConversation?.targetId === conv.targetId && activeConversation?.sessionType === conv.sessionType}
      onSelect={() => {
        setActiveConversation(conv)
        const path = conv.sessionType === 'group' ? `/chat/group/${conv.targetId}` : `/chat/${conv.targetId}`
        navigate(path, { replace: true })
      }}
      onTogglePin={() => togglePin(conv.targetId, conv.sessionType)}
      onToggleDnd={() => toggleDnd(conv.targetId, conv.sessionType)}
      onDelete={(e) => handleDeleteClick(conv.targetId, conv.sessionType, e)}
    />
  )

  return (
    <div className="flex-1 overflow-y-auto">
      {pinnedConvs.length > 0 && (
        <>
          <div className="px-4 pt-3 pb-1.5 text-[11px] font-medium text-text-secondary uppercase tracking-wide">
            Pinned
          </div>
          {pinnedConvs.map(renderRow)}
        </>
      )}
      {normalConvs.length > 0 && (
        <>
          {pinnedConvs.length > 0 && (
            <div className="px-4 pt-2 pb-1.5 text-[11px] font-medium text-text-secondary uppercase tracking-wide">
              Recent
            </div>
          )}
          {normalConvs.map(renderRow)}
        </>
      )}

      <ConfirmDialog
        open={deletingConv !== null}
        onClose={() => setDeletingConv(null)}
        onConfirm={handleDeleteConfirm}
        title="Delete Conversation"
        message="This will remove the conversation from your list. Message history will be preserved."
        confirmLabel="Delete"
        danger
      />
    </div>
  )
}



const ConversationRow = memo(function ConversationRow({
  conv,
  isActive,
  onSelect,
  onTogglePin,
  onToggleDnd,
  onDelete,
}: {
  conv: ReturnType<typeof useChatStore.getState>['conversations'][number]
  isActive: boolean
  onSelect: () => void
  onTogglePin: () => void
  onToggleDnd: () => void
  onDelete: (e: React.MouseEvent) => void
}) {
  const isGroup = conv.sessionType === 'group'
  const isPinned = conv.isPinned === 1
  const isDnd = conv.dnd === true
  const displayName = conv.memo || conv.nickname

  return (
    <div className="group">
      <button
        onClick={onSelect}
        className={`w-full flex items-center gap-3 px-4 py-3 text-left cursor-pointer transition-colors duration-150 select-none ${
          isActive ? 'bg-primary-50' : 'hover:bg-surface-hover active:bg-surface-active'
        }`}
      >
        <div className="relative shrink-0">
          {isGroup ? (
            <Avatar src={conv.avatar} fallback={displayName} size="lg" />
          ) : (
            <Avatar src={conv.avatar} fallback={displayName} size="lg" />
          )}
          {conv.unreadCount > 0 && !isActive && (
            <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 flex items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white leading-none">
              {conv.unreadCount > 99 ? '99+' : conv.unreadCount}
            </span>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1">
            <span className={`text-sm truncate flex-1 ${conv.unreadCount > 0 && !isActive ? 'font-semibold text-text-primary' : 'font-medium text-text-primary'}`}>
              {displayName}
            </span>
            <span
              onClick={(e) => {
                e.stopPropagation()
                onTogglePin()
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  e.stopPropagation()
                  onTogglePin()
                }
              }}
              role="button"
              tabIndex={0}
              className={`p-0.5 rounded transition-all ${
                isPinned
                  ? 'text-primary-500'
                  : 'text-text-secondary opacity-0 group-hover:opacity-100'
              } hover:text-primary-600 hover:bg-primary-50 cursor-pointer`}
              title={isPinned ? 'Unpin conversation' : 'Pin conversation'}
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill={isPinned ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="17" x2="12" y2="22" />
                <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z" />
              </svg>
            </span>
            {conv.lastMsgTime && (
              <span className="text-[10px] text-text-secondary shrink-0">
                {formatTime(conv.lastMsgTime)}
              </span>
            )}
            {!isGroup && (
              <span
                onClick={(e) => {
                  e.stopPropagation()
                  onToggleDnd()
                }}
                className={`p-0.5 rounded transition-all cursor-pointer ${
                  isDnd
                    ? 'text-text-secondary'
                    : 'text-text-secondary opacity-0 group-hover:opacity-100'
                } hover:text-text-primary hover:bg-surface-active`}
                title={isDnd ? 'Unmute conversation' : 'Mute conversation'}
              >
                {isDnd ? (
                  <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="1" y1="1" x2="23" y2="23" /><path d="M9 9v3a3 3 0 005.12 2.12M15 9.34V4a3 3 0 00-5.94-.6" /><path d="M17 16.95A7 7 0 015 12v-2m14 0v2a7 7 0 01-.11 1.23" /><line x1="12" y1="19" x2="12" y2="23" /><line x1="8" y1="23" x2="16" y2="23" />
                  </svg>
                ) : (
                  <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" /><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07" />
                  </svg>
                )}
              </span>
            )}
            <span
              onClick={(e) => {
                e.stopPropagation()
                onDelete(e)
              }}
              className="p-0.5 rounded text-text-secondary opacity-0 group-hover:opacity-100 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950 cursor-pointer transition-all"
              title="Delete conversation"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
            </span>
          </div>
          <p className={`text-xs truncate mt-0.5 ${conv.unreadCount > 0 && !isActive ? 'text-text-primary font-medium' : 'text-text-secondary'}`}>
            {formatLastMsg(conv.lastMsg)}
          </p>
        </div>
      </button>
    </div>
  )
})
