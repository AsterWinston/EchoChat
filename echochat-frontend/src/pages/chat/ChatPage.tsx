import { useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { useChatStore } from '@/stores/chatStore'
import ChatWindow from '@/components/chat/ChatWindow'



export default function ChatPage() {
  const { uid } = useParams<{ uid: string }>()
  const { gid } = useParams<{ gid: string }>()
  const activeConversation = useChatStore((s) => s.activeConversation)
  const setActiveConversation = useChatStore((s) => s.setActiveConversation)
  const conversations = useChatStore((s) => s.conversations)
  const prevTargetId = useRef<string | null>(null)

  useEffect(() => {
    const targetId = uid || gid || null
    if (targetId === prevTargetId.current) return
    prevTargetId.current = targetId

    if (!targetId) {
      setActiveConversation(null)
      return
    }

    const sessionType = gid ? 'group' : 'single'
    const existing = conversations.find(
      (c) => c.targetId === targetId && c.sessionType === sessionType,
    )
    if (existing) {
      setActiveConversation(existing)
    } else {
      const profile = useChatStore.getState().userProfiles[targetId]
      setActiveConversation({
        targetId,
        sessionType: (gid ? 'group' : 'single') as 'single' | 'group',
        lastMsgId: null,
        unreadCount: 0,
        isPinned: 0,
        updatedAt: '',
        lastMsg: null,
        lastMsgTime: null,
        nickname: profile?.nickname || (gid ? 'Group' : targetId),
        memo: profile?.memo,
        avatar: profile?.avatar || '',
        online: false,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid, gid])

  if (activeConversation) {
    return <ChatWindow />
  }

  return (
    <div className="flex-1 flex items-center justify-center animate-fade-in">
      <div className="text-center text-text-secondary">
        <div className="w-20 h-20 rounded-2xl bg-primary-100 flex items-center justify-center mx-auto mb-4">
          <svg
            className="w-10 h-10 text-primary-500"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
          </svg>
        </div>
        <h3 className="text-lg font-medium text-text-primary mb-1">
          Welcome to EchoChat
        </h3>
        <p className="text-sm">
          Select a conversation to start chatting
        </p>
      </div>
    </div>
  )
}
