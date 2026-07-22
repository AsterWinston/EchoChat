import { useState, useRef, useEffect, memo } from 'react'
import type { Message } from '@/types/message'
import Avatar from '@/components/ui/Avatar'
import ContextMenu, { type ContextMenuItem } from '@/components/ui/ContextMenu'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { convKey } from '@/utils/convKey'
import ImageMessage from '@/components/chat/ImageMessage'
import VideoMessage from '@/components/chat/VideoMessage'
import VoiceMessage from '@/components/chat/VoiceMessage'
import FileMessage from '@/components/chat/FileMessage'
import { addFavorite } from '@/api/favorite'
import { getReadReceipt, pinMessage, unpinMessage } from '@/api/message'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import ReadReceiptModal from '@/components/chat/ReadReceiptModal'
import { toast } from '@/components/ui/Toast'

interface ChatBubbleProps {
  msg: Message
  isOwn: boolean
  ownAvatar?: string
  ownName?: string
  otherAvatar?: string
  otherName?: string
  showSenderName?: boolean
  showStatus?: boolean
  onReply?: (msg: Message) => void
  onForward?: (msg: Message) => void
  onDelete?: (msgId: string) => void
  onAvatarClick?: (uid: string) => void
  isPinned?: boolean
  onPinChange?: () => void
}

function canRecall(createdAt?: string): boolean {
  if (!createdAt) return false
  const age = Date.now() - new Date(createdAt).getTime()
  return age < 2 * 60 * 1000
}

function ChatBubbleInner({ msg, isOwn, ownAvatar, ownName, otherAvatar, otherName, showSenderName, showStatus, onReply, onForward, onDelete, onAvatarClick, isPinned, onPinChange }: ChatBubbleProps) {
  const bubbleRef = useRef<HTMLDivElement>(null)
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const touchPos = useRef({ x: 0, y: 0 })

  const [showRecallConfirm, setShowRecallConfirm] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showReadReceipt, setShowReadReceipt] = useState(false)
  const [readCount, setReadCount] = useState<number | null>(null)
  const realtimeGroupRead = useChatStore((s) => s.groupReadCounts[String(msg.msgId)])

  const recallMessage = useChatStore((s) => s.recallMessage)
  const isFailed = msg._failed === true
  const isUploading = msg._uploading === true
  const uploadProgress = msg._uploadProgress ?? 0
  const withinWindow = canRecall(msg.createdAt)

  const repliedToMsg = useChatStore((s) => {
    if (!msg.replyToMsgId || !s.activeConversation) return null
    const msgs = s.messages[convKey(s.activeConversation.targetId, s.activeConversation.sessionType)]
    if (!msgs) return null
    return msgs.find((m) => String(m.msgId) === String(msg.replyToMsgId)) ?? null
  })
  const repliedToName = useChatStore((s) => {
    if (!repliedToMsg) return null
    const uid = String(repliedToMsg.fromUid)
    const profile = s.userProfiles[uid]
    if (profile?.nickname) return profile.memo || profile.nickname
    const conv = s.conversations.find((c) => c.targetId === uid && c.sessionType === 'single')
    if (conv?.nickname && conv.nickname !== uid) return conv.memo || conv.nickname
    return uid
  })

  const loadUserProfile = useChatStore((s) => s.loadUserProfile)

  useEffect(() => {
    if (!repliedToMsg) return
    const uid = String(repliedToMsg.fromUid)
    const profile = useChatStore.getState().userProfiles[uid]
    if (profile?.nickname) return
    const conv = useChatStore.getState().conversations.find(
      (c) => c.targetId === uid && c.sessionType === 'single',
    )
    if (conv?.nickname && conv.nickname !== uid) return
    loadUserProfile(uid)
  }, [repliedToMsg, loadUserProfile])

  useEffect(() => {
    if (!isOwn || msg.sessionType !== 'group' || isFailed || !msg.msgId) return
    getReadReceipt(String(msg.msgId)).then((r) => {
      setReadCount(r.readCount)
    }).catch((e) => { console.error('Failed to get read receipt:', e) })
  }, [msg.msgId, isOwn, msg.sessionType, isFailed])

  useEffect(() => {
    if (!realtimeGroupRead) return
    getReadReceipt(String(msg.msgId)).then((r) => {
      setReadCount(r.readCount)
    }).catch((e) => { console.error('Failed to refresh group read count:', e) })
  }, [realtimeGroupRead])

  const handleRecall = async () => {
    setShowRecallConfirm(false)
    const ok = await recallMessage(String(msg.msgId))
    toast(ok ? 'Recalled' : 'Recall failed', ok ? 'success' : 'error')
  }

  const handleDelete = () => {
    setShowDeleteConfirm(false)
    onDelete?.(String(msg.msgId))
    toast('Deleted', 'success')
  }

  if (isUploading && isOwn) {
    return (
      <div ref={bubbleRef} className="flex justify-end px-4 py-1 animate-message-in">
        <div className="max-w-[70%] min-w-0 flex flex-row-reverse gap-2">
          <Avatar src={ownAvatar} fallback={ownName || '?'} size="sm" className="shrink-0 mt-0.5" />
          <div className="bg-primary-500 text-white rounded-2xl rounded-br-md px-3.5 py-2.5 min-w-[120px]">
            <div className="flex items-center gap-2 mb-1.5">
              <svg className="w-4 h-4 animate-spin text-white/80" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span className="text-xs text-white/80">Uploading...</span>
            </div>
            <div className="bg-white/20 rounded-full h-1 overflow-hidden">
              <div className="bg-white h-1 rounded-full transition-all duration-300" style={{ width: `${uploadProgress}%` }} />
            </div>
            <span className="text-[10px] text-white/60 mt-1 block">{uploadProgress}%</span>
          </div>
        </div>
      </div>
    )
  }

  if (msg.msgType === 'SYSTEM') {
    return (
      <div className="relative flex justify-center py-1">
        <span className="text-xs text-primary-700 bg-primary-50 dark:bg-primary-900/20 dark:text-primary-300 rounded-lg px-3 py-1">
          <SystemMsgText content={msg.content} />
        </span>
      </div>
    )
  }

  if (msg.isRecalled === 1) {
    return (
      <div className="relative flex justify-center py-1">
        <span className="text-xs text-primary-700 bg-primary-50 dark:bg-primary-900/20 dark:text-primary-300 rounded-lg px-3 py-1">
          {isOwn ? 'You recalled a message' : 'A message was recalled'}
        </span>
      </div>
    )
  }

  const contextItems: ContextMenuItem[] = []
  if (isUploading) {
    contextItems.length = 0
  } else   if (!isFailed) {
    contextItems.push({ label: 'Reply', onClick: () => onReply?.(msg) })
    if (msg.msgType === 'TEXT') {
      contextItems.push({ label: 'Copy', onClick: () => { navigator.clipboard.writeText(msg.content).then(() => toast('Copied', 'success')).catch((e) => { console.error('Failed to copy text:', e) }) } })
    }
    contextItems.push({ label: 'Forward', onClick: () => onForward?.(msg) })
    contextItems.push({ label: 'Favorite', onClick: async () => {
      try { await addFavorite(String(msg.msgId)); toast('Added to favorites', 'success') } catch { toast('Already favorited', 'error') }
    }})
    if (isPinned) {
      contextItems.push({ label: 'Unpin', onClick: async () => {
        try { await unpinMessage(String(msg.msgId)); toast('Unpinned', 'success'); onPinChange?.() } catch (err) { toast(err instanceof Error ? err.message : 'Unpin failed', 'error') }
      }})
    } else {
      contextItems.push({ label: 'Pin', onClick: async () => {
        try {
          const targetUid = msg.sessionType === 'group' ? msg.toId : (isOwn ? msg.toId : String(msg.fromUid))
          await pinMessage(String(msg.msgId), targetUid, msg.content.slice(0, 50))
          toast('Pinned', 'success')
          onPinChange?.()
        } catch (err) { toast(err instanceof Error ? err.message : 'Pin failed', 'error') }
      }})
    }
  }
  if (isOwn && !isFailed && withinWindow) {
    contextItems.push({ label: 'Recall', danger: true, onClick: () => setShowRecallConfirm(true) })
  }
  if (!isFailed) {
    contextItems.push({ label: 'Delete', danger: true, onClick: () => setShowDeleteConfirm(true) })
  }

  const bubble = (
    <div ref={bubbleRef} id={`msg-${msg.msgId}`} className={`flex ${isOwn ? 'justify-end' : 'justify-start'} px-4 py-1 animate-message-in select-none`}
      onTouchStart={(e) => {
        if (e.touches.length !== 1) return
        touchPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
        longPressTimer.current = setTimeout(() => {
          const rect = bubbleRef.current?.getBoundingClientRect()
          const evt = new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: touchPos.current.x || ((rect?.right ?? 0) - 40), clientY: touchPos.current.y ?? rect?.bottom ?? 0 })
          bubbleRef.current?.dispatchEvent(evt)
        }, 500)
      }}
      onTouchMove={() => { if (longPressTimer.current) clearTimeout(longPressTimer.current) }}
      onTouchEnd={() => { if (longPressTimer.current) clearTimeout(longPressTimer.current) }}>
      <div className={`max-w-[70%] min-w-0 flex ${isOwn ? 'flex-row-reverse items-start' : 'flex-row items-start'} gap-2`}>
        {!isOwn && onAvatarClick ? (
          <button onClick={() => onAvatarClick(String(msg.fromUid))} className="shrink-0 cursor-pointer p-0">
            <Avatar src={otherAvatar} fallback={otherName || '?'} size="sm" className={showSenderName ? '' : 'mt-0.5'} />
          </button>
        ) : (
          <Avatar src={isOwn ? ownAvatar : otherAvatar} fallback={isOwn ? (ownName || '?') : (otherName || '?')} size="sm" className={`shrink-0 ${showSenderName ? '' : 'mt-0.5'}`} />
        )}
        <div className="min-w-0">
          {showSenderName && !isOwn && (
            onAvatarClick ? (
              <button onClick={() => onAvatarClick(String(msg.fromUid))} className="text-xs text-text-secondary mb-1 font-medium hover:text-primary-500 cursor-pointer transition-colors text-left">
                {otherName}
              </button>
            ) : (
              <p className="text-xs text-text-secondary mb-1 font-medium">{otherName}</p>
            )
          )}
          <div className={`min-w-0 px-3.5 py-2.5 text-sm leading-relaxed whitespace-pre-wrap break-words select-text ${isOwn ? 'bg-primary-500 text-white rounded-2xl rounded-br-md' : 'bg-surface text-text-primary rounded-2xl rounded-bl-md shadow-sm'} ${isFailed ? 'opacity-60' : ''}`}>
            {repliedToMsg && repliedToMsg.isRecalled !== 1 && (
              <div className={`mb-1.5 border-l-2 rounded-r px-2 py-1 text-xs ${isOwn ? 'border-white/60 text-white/80 bg-white/10' : 'border-primary-400 text-primary-600 bg-primary-50'}`}>
                <span className="font-medium block truncate">{repliedToName || 'User'}</span>
                <p className="truncate opacity-80">{repliedToMsg.content}</p>
              </div>
            )}
            <MessageBody msg={msg} isOwn={isOwn} />
          </div>
          {isOwn && msg.sessionType !== 'group' && (
            <div className="flex items-center justify-end gap-0.5 mt-0.5 min-h-[14px]">
              {isFailed && <svg className="w-3.5 h-3.5 text-red-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>}
              {!isFailed && showStatus !== false && msg.status === 2 && <svg className="w-3.5 h-3.5 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /><polyline points="20 6 9 17 4 12" transform="translate(2,0)" /></svg>}
              {!isFailed && showStatus !== false && msg._localSent && !msg.status && <svg className="w-3.5 h-3.5 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>}
              {!isFailed && showStatus !== false && !msg._localSent && msg.status === 1 && <svg className="w-3.5 h-3.5 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /><polyline points="20 6 9 17 4 12" transform="translate(2,0)" /></svg>}
            </div>
          )}
          {isOwn && msg.sessionType === 'group' && !isFailed && (
            <button
              onClick={() => setShowReadReceipt(true)}
              className="flex justify-end mt-0.5"
            >
              <span className="text-[10px] text-text-secondary hover:text-primary-500 cursor-pointer transition-colors">
                {readCount !== null ? `${readCount} read` : 'Read'}
              </span>
            </button>
          )}
        </div>
      </div>
    </div>
  )

  return (
    <>
      {contextItems.length > 0 ? <ContextMenu items={contextItems}>{bubble}</ContextMenu> : bubble}
      <ConfirmDialog
        open={showRecallConfirm}
        onClose={() => setShowRecallConfirm(false)}
        onConfirm={handleRecall}
        title="Recall Message"
        message="This message will be recalled and replaced with a system notice."
        confirmLabel="Recall"
        danger
      />
      <ConfirmDialog
        open={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        onConfirm={handleDelete}
        title="Delete Message"
        message="This message will be removed from your view only. Other participants will still see it."
        confirmLabel="Delete"
        danger
      />
      {msg.sessionType === 'group' && (
        <ReadReceiptModal
          open={showReadReceipt}
          msgId={String(msg.msgId)}
          gid={msg.toId}
          onClose={() => setShowReadReceipt(false)}
        />
      )}
    </>
  )
}

export default memo(ChatBubbleInner)

function MessageBody({ msg, isOwn }: { msg: Message; isOwn: boolean }) {
  if (msg.msgType === 'TEXT') return <MentionText content={msg.content} mentionedUids={msg.mentionedUids} isOwn={isOwn} />

  if (msg.msgType === 'IMAGE') {
    try { const data = JSON.parse(msg.content); const img = Array.isArray(data) ? data[0] : data; return <ImageMessage url={img?.url} fileId={img?.fileId} fileName={img?.name} /> }
    catch { return <ImageMessage url={msg.content} /> }
  }
  if (msg.msgType === 'VIDEO') {
    try { const data = JSON.parse(msg.content); return <VideoMessage url={data.url} fileId={data.fileId} fileName={data.name} /> }
    catch { return <VideoMessage url={msg.content} /> }
  }
  if (msg.msgType === 'VOICE') {
    try { const data = JSON.parse(msg.content); return <VoiceMessage fileId={data.fileId} duration={data.duration} isOwn={isOwn} /> }
    catch { return <VoiceMessage fileId={msg.content} isOwn={isOwn} /> }
  }
  if (msg.msgType === 'FILE') {
    try { const data = JSON.parse(msg.content); return <FileMessage url={data.url} fileName={data.name} fileSize={data.size} fileId={data.fileId} /> }
    catch { return <FileMessage url={msg.content} fileName="File" fileSize={0} /> }
  }

  return <>{msg.content}</>
}

function MentionText({ content, mentionedUids, isOwn }: { content: string; mentionedUids?: string | null; isOwn: boolean }) {
  const userProfiles = useChatStore((s) => s.userProfiles)
  const myUid = useAuthStore((s) => s.user?.uid)

  useEffect(() => {
    const uidsInContent = Array.from(new Set(
      Array.from(content.matchAll(/<@(\w+)>/g))
        .map((m) => m[1])
        .filter((uid) => uid !== 'all'),
    ))
    const { userProfiles: profiles, loadUserProfile } = useChatStore.getState()
    for (const uid of uidsInContent) {
      if (!profiles[uid]?.nickname) {
        loadUserProfile(uid)
      }
    }
  }, [content])

  let mentionUidSet: Set<string>
  try {
    mentionUidSet = new Set(mentionedUids ? JSON.parse(mentionedUids).map(String) : [])
  } catch {
    mentionUidSet = new Set()
  }

  const parts = content.split(/(<@\w+>)/g)
  if (parts.length <= 1) return <>{content}</>

  return (
    <>
      {parts.map((part, i) => {
        const match = part.match(/^<@(\w+)>$/)
        if (!match) return <span key={i}>{part}</span>

        const uid = match[1]
        if (uid === 'all') {
          return (
            <span key={i} className={
              isOwn
                ? 'inline-flex items-center gap-0.5 bg-white/20 text-white rounded px-1 py-0.5 text-[0.85em] font-medium'
                : 'inline-flex items-center gap-0.5 text-primary-600 dark:text-primary-400 font-medium text-[0.85em]'
            }>
              @Everyone
            </span>
          )
        }

        const profile = userProfiles[uid]
        const name = (profile?.memo || profile?.nickname) || uid
        const isMe = String(uid) === String(myUid)
        const isExplicitlyMentioned = mentionUidSet.has(uid)

        if (isMe || isExplicitlyMentioned) {
          return (
            <span key={i} className={
              isOwn
                ? 'inline-flex items-center gap-0.5 bg-amber-300/30 text-amber-200 rounded px-1 py-0.5 text-[0.85em] font-medium'
                : 'inline-flex items-center gap-0.5 text-amber-600 dark:text-amber-400 font-medium text-[0.85em]'
            }>
              @{name}
            </span>
          )
        }

        return (
          <span key={i} className={
            isOwn
              ? 'inline-flex items-center gap-0.5 text-white font-semibold text-[0.85em]'
              : 'inline-flex items-center gap-0.5 text-primary-600 dark:text-primary-400 font-medium text-[0.85em]'
          }>
            @{name}
          </span>
        )
      })}
    </>
  )
}

function SystemMsgText({ content }: { content: string }) {
  try {
    const data = JSON.parse(content)
    const name = data.name || data.uid || ''
    switch (data.type) {
      case 'join': return <>{name} joined the group</>
      case 'leave': return <>{name} left the group</>
      case 'kick': return <>{name} was removed from the group</>
      default: return <>{content}</>
    }
  } catch {
    return <>{content}</>
  }
}
