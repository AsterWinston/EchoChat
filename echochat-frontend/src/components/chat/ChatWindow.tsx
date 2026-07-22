import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import ChatHeader from '@/components/chat/ChatHeader'
import ChatBubble from '@/components/chat/ChatBubble'
import ChatInput from '@/components/chat/ChatInput'
import Modal from '@/components/ui/Modal'
import Avatar from '@/components/ui/Avatar'
import { convKey } from '@/utils/convKey'
import type { Message } from '@/types/message'
import { getPinnedMessages, unpinMessage, type PinnedMsg } from '@/api/message'
import { searchMessagesWithUser } from '@/api/search'
import { formatTime } from '@/utils/time'

const EMPTY_MSGS: Message[] = []

function formatForwardPreview(content: string, msgType: string): string {
  if (msgType === 'TEXT' || !content) return content
  if (msgType === 'IMAGE') return '[Image]'
  if (msgType === 'VIDEO') return '[Video]'
  if (msgType === 'VOICE') return '[Voice]'
  if (msgType === 'FILE') {
    try {
      const d = JSON.parse(content)
      return `[File] ${d.name || 'File'}`
    } catch { return '[File]' }
  }
  return content
}



export default function ChatWindow() {
  const conv = useChatStore((s) => s.activeConversation)
  const conversations = useChatStore((s) => s.conversations)
  const messages = useChatStore(
    (s) => {
      if (!s.activeConversation) return EMPTY_MSGS
      return s.messages[convKey(s.activeConversation.targetId, s.activeConversation.sessionType)] ?? EMPTY_MSGS
    },
  )
  const messagesLoading = useChatStore((s) => s.messagesLoading)
  const messagesError = useChatStore((s) => s.messagesError)
  const olderLoading = useChatStore((s) => s.olderLoading)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const refreshMessages = useChatStore((s) => s.refreshMessages)
  const loadOlderMessages = useChatStore((s) => s.loadOlderMessages)
  const forwardMessage = useChatStore((s) => s.forwardMessage)
  const deleteMessage = useChatStore((s) => s.deleteMessage)
  const markRead = useChatStore((s) => s.markRead)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const user = useAuthStore((s) => s.user)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const scrollRef = useRef<HTMLDivElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const isNearBottom = useRef(true)
  const savedScrollHeight = useRef(0)
  const skipAutoScroll = useRef(false)

  const doScrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [])

  const targetId = conv?.targetId ?? ''
  const sessionType = conv?.sessionType ?? 'single'

  const [replyingTo, setReplyingTo] = useState<{ msgId: string; content: string; nickname: string } | null>(null)
  const [forwardMsg, setForwardMsg] = useState<Message | null>(null)
  const [forwardSelected, setForwardSelected] = useState<Set<string>>(new Set())
  const navigate = useNavigate()

  const handleProfileClick = useCallback((uid: string) => {
    navigate(`/profile/${uid}`)
  }, [navigate])
  const [pinnedMsgs, setPinnedMsgs] = useState<PinnedMsg[]>([])
  const [showPinned, setShowPinned] = useState(false)
  const [pinnedRefresh, setPinnedRefresh] = useState(0)
  const pinnedMsgIds = useMemo(() => new Set(pinnedMsgs.map(p => p.msgId)), [pinnedMsgs])

  const [convSearchOpen, setConvSearchOpen] = useState(false)
  const [convSearchQuery, setConvSearchQuery] = useState('')
  const [convSearchResults, setConvSearchResults] = useState<Array<{ msgId: string; content: string; createdAt: string }>>([])
  const [convSearchLoading, setConvSearchLoading] = useState(false)

  const refreshPinned = useCallback(() => setPinnedRefresh(n => n + 1), [])

  const handleSend = useCallback((content: string, replyToMsgId?: string, msgType?: string) => {
    doScrollToBottom()
    sendMessage(content, replyToMsgId, msgType)
    setReplyingTo(null)
  }, [sendMessage, doScrollToBottom])

  const handleReply = useCallback((msg: Message) => {
    const own = String(msg.fromUid) === String(user?.uid)
    let name: string
    if (own) {
      name = user?.nickname || 'You'
    } else if (sessionType === 'group') {
      name = userProfiles[String(msg.fromUid)]?.nickname || String(msg.fromUid)
    } else {
      const otherName = conv?.memo || conv?.nickname || String(msg.fromUid)
      name = otherName
    }
    setReplyingTo({ msgId: String(msg.msgId), content: msg.content, nickname: name })
  }, [user, userProfiles, conv, sessionType])

  const handleForward = useCallback((msg: Message) => {
    setForwardMsg(msg)
  }, [])

  const handleDelete = useCallback((msgId: string) => {
    deleteMessage(msgId)
  }, [deleteMessage])

  const handleScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    isNearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    if (el.scrollTop < 40 && targetId && !olderLoading) {
      skipAutoScroll.current = true
      savedScrollHeight.current = el.scrollHeight
      loadOlderMessages(targetId, sessionType)
    }
  }, [targetId, sessionType, loadOlderMessages, olderLoading])

  useLayoutEffect(() => {
    if (skipAutoScroll.current) {
      const el = scrollRef.current
      if (el) el.scrollTop = el.scrollHeight - savedScrollHeight.current
      skipAutoScroll.current = false
      return
    }
    if (isNearBottom.current) {
      doScrollToBottom()
    }
  }, [messages, doScrollToBottom])

  useEffect(() => {
    isNearBottom.current = true
  }, [targetId])


  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const observer = new MutationObserver(() => {
      if (isNearBottom.current) {
        doScrollToBottom()
      }
    })
    observer.observe(el, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [doScrollToBottom])

  useEffect(() => {
    if (!targetId) return
    const interval = setInterval(() => {
      refreshMessages(targetId, sessionType)
    }, 5000)
    return () => clearInterval(interval)
  }, [targetId, sessionType, refreshMessages])

  useEffect(() => {
    if (!targetId) return
    getPinnedMessages(sessionType, targetId)
      .then(setPinnedMsgs)
      .catch(() => setPinnedMsgs([]))
  }, [targetId, sessionType, pinnedRefresh])

  useEffect(() => {
    const onFocus = () => {
      if (conv && document.visibilityState === 'visible' && document.hasFocus()) {
        markRead(conv.targetId, conv.sessionType)
      }
    }
    document.addEventListener('visibilitychange', onFocus)
    window.addEventListener('focus', onFocus)
    return () => {
      document.removeEventListener('visibilitychange', onFocus)
      window.removeEventListener('focus', onFocus)
    }
  }, [conv, markRead])

  const [searchParams] = useSearchParams()
  const msgParam = searchParams.get('msg')
  const pendingScrollMsgId = useChatStore((s) => s.pendingScrollMsgId)
  const jumpToMessage = useChatStore((s) => s.jumpToMessage)
  const lastJumpMsgParam = useRef<string | null>(null)

  if (!conv) return null

  const sorted = useMemo(() =>
    messages,
    [messages],
  )

  useEffect(() => {
    if (!convSearchQuery.trim() || sessionType === 'group') {
      setConvSearchResults([])
      return
    }
    const timer = setTimeout(async () => {
      setConvSearchLoading(true)
      try {
        const results = await searchMessagesWithUser(convSearchQuery, targetId)
        setConvSearchResults(results.map((r) => ({ msgId: String(r.msgId), content: r.content, createdAt: r.createdAt })))
      } catch {
        setConvSearchResults([])
      } finally {
        setConvSearchLoading(false)
      }
    }, 300)
    return () => clearTimeout(timer)
  }, [convSearchQuery, targetId, sessionType])

  const scrollToMsg = useCallback((msgId: string) => {
    const el = document.getElementById(`msg-${msgId}`)
    if (!el) {
      useChatStore.getState().jumpToMessage(targetId, sessionType, msgId)
      return
    }
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.style.transition = 'background-color 0.5s ease'
    el.style.backgroundColor = 'rgba(59,130,246,0.15)'
    setTimeout(() => {
      el.style.backgroundColor = ''
      el.style.transition = ''
    }, 2000)
  }, [targetId, sessionType])

  useEffect(() => {
    if (!msgParam || !targetId) return
    if (msgParam === lastJumpMsgParam.current) return
    lastJumpMsgParam.current = msgParam
    jumpToMessage(targetId, sessionType, msgParam)
  }, [msgParam, targetId, sessionType, jumpToMessage])

  useEffect(() => {
    if (!pendingScrollMsgId) return
    const el = document.getElementById(`msg-${pendingScrollMsgId}`)
    if (!el) return
    const doScroll = () => {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.style.transition = 'background-color 0.3s ease'
      el.style.backgroundColor = 'rgba(59,130,246,0.15)'
      setTimeout(() => {
        el.style.backgroundColor = ''
        el.style.transition = ''
      }, 2000)
    }
    requestAnimationFrame(() => requestAnimationFrame(doScroll))
    useChatStore.setState({ pendingScrollMsgId: null })
  }, [sorted, pendingScrollMsgId])

  useEffect(() => {
    lastJumpMsgParam.current = null
  }, [targetId])

  useEffect(() => {
    if (sessionType !== 'group') return
    const myUid = String(user?.uid ?? '')
    for (const msg of sorted) {
      const uid = String(msg.fromUid)
      if (uid === '0') continue
      if (uid !== myUid && !userProfiles[uid]) {
        loadUserProfile(uid)
      }
    }
  }, [sorted, sessionType, user?.uid, userProfiles, loadUserProfile])

  const forwardTargets = useMemo(() =>
    conversations.filter((c) =>
      (c.sessionType === 'single' || c.sessionType === 'group') &&
      !(c.targetId === conv.targetId && c.sessionType === conv.sessionType)
    ),
    [conversations, conv.targetId, conv.sessionType],
  )

  const handleForwardConfirm = useCallback(async (targetId: string, targetSessionType: string) => {
    if (!forwardMsg) return
    if (targetSessionType === 'group') {
      await forwardMessage(targetId, String(forwardMsg.msgId), 'group')
    } else {
      await forwardMessage(targetId, String(forwardMsg.msgId))
    }
    setForwardMsg(null)
  }, [forwardMsg, forwardMessage])

  const toggleForwardTarget = useCallback((convKey: string) => {
    setForwardSelected((prev) => {
      const next = new Set(prev)
      if (next.has(convKey)) next.delete(convKey)
      else next.add(convKey)
      return next
    })
  }, [])

  const handleSendAll = useCallback(async () => {
    if (!forwardMsg) return
    for (const key of forwardSelected) {
      const [sessionType, targetId] = key.split(':')
      await handleForwardConfirm(targetId, sessionType)
    }
    setForwardSelected(new Set())
    setForwardMsg(null)
  }, [forwardMsg, forwardSelected, handleForwardConfirm])

  return (
    <div id="chat-area" className="flex-1 flex flex-col min-h-0 animate-fade-in relative">
      <ChatHeader
        conv={conv}
        onAvatarClick={() => handleProfileClick(conv.targetId)}
        searchOpen={convSearchOpen}
        onToggleSearch={() => { setConvSearchOpen((v) => !v); setConvSearchQuery(''); setConvSearchResults([]) }}
      />

      {sessionType === 'single' && convSearchOpen && (
        <div className="flex items-center gap-2 px-5 py-1.5 bg-surface border-b border-border-light shrink-0">
          <div className="flex-1 flex items-center gap-2">
            <input
              type="text"
              value={convSearchQuery}
              onChange={(e) => setConvSearchQuery(e.target.value)}
              placeholder="Search messages..."
              className="flex-1 bg-surface-active rounded-lg px-2.5 py-1 text-xs text-text-primary placeholder:text-text-secondary outline-none focus:ring-1 focus:ring-primary-200"
              autoFocus
            />
            {convSearchQuery && (
              <button
                onClick={() => { setConvSearchQuery(''); setConvSearchResults([]) }}
                className="text-text-secondary hover:text-text-primary cursor-pointer text-xs shrink-0"
              >
                Clear
              </button>
            )}
            {convSearchResults.length > 0 && (
              <span className="text-[10px] text-text-secondary shrink-0">{convSearchResults.length} matches</span>
            )}
          </div>
        </div>
      )}
      {convSearchOpen && convSearchResults.length > 0 && (
        <div className="bg-surface border-b border-border-light max-h-36 overflow-y-auto shrink-0">
          {convSearchResults.map((r) => (
            <button
              key={r.msgId}
              onClick={() => { scrollToMsg(r.msgId); setConvSearchOpen(false); setConvSearchQuery(''); setConvSearchResults([]) }}
              className="w-full flex items-center gap-3 px-5 py-1.5 text-left hover:bg-surface-hover transition-colors cursor-pointer"
            >
              <span className="text-[10px] text-text-secondary shrink-0">{formatTime(r.createdAt)}</span>
              <span className="text-xs text-text-primary truncate">{r.content.length > 80 ? r.content.slice(0, 80) + '...' : r.content}</span>
            </button>
          ))}
        </div>
      )}
      {convSearchOpen && convSearchLoading && (
        <div className="bg-surface border-b border-border-light px-5 py-2 shrink-0 text-xs text-text-secondary">
          Searching...
        </div>
      )}

      {pinnedMsgs.length > 0 && (
        <div className="bg-primary-50 dark:bg-primary-900/20 border-b border-primary-100 dark:border-primary-900/30 px-4 py-1.5 flex items-center gap-2 text-xs shrink-0 cursor-pointer"
             onClick={() => setShowPinned(!showPinned)}>
          <svg className="w-3.5 h-3.5 text-primary-500 shrink-0" viewBox="0 0 24 24" fill="currentColor"><line x1="12" y1="17" x2="12" y2="22"/><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/></svg>
          <span className="text-primary-600 dark:text-primary-400 flex-1 truncate">
            {pinnedMsgs.length > 1 ? `${pinnedMsgs.length} pinned messages` : (pinnedMsgs[0].contentSummary || pinnedMsgs[0].content?.slice(0, 50) || 'Pinned message')}
          </span>
          <span className="text-primary-400 text-[10px]">{showPinned ? '▲' : '▼'}</span>
        </div>
      )}
      {showPinned && pinnedMsgs.length > 0 && (
        <div className="bg-surface border-b border-border-light px-4 py-2 max-h-32 overflow-y-auto shrink-0">
          {pinnedMsgs.map((p) => (
            <div key={p.msgId} className="flex items-center gap-2 text-xs py-0.5">
              <span className="text-text-secondary shrink-0">📌</span>
              <span className="text-text-primary truncate flex-1">{p.contentSummary || p.content?.slice(0, 80) || 'Pinned'}</span>
              <button onClick={(e) => {
                e.stopPropagation()
                const el = document.getElementById(`msg-${p.msgId}`)
                el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
              }} className="text-primary-500 hover:text-primary-400 cursor-pointer shrink-0">Jump</button>
              <button onClick={async (e) => {
                e.stopPropagation()
                try { await unpinMessage(p.msgId); refreshPinned() } catch {}
              }} className="text-text-secondary hover:text-red-500 cursor-pointer shrink-0 text-xs px-1" title="Unpin">✕</button>
            </div>
          ))}
        </div>
      )}

      <div ref={scrollRef} onScroll={handleScroll} className="flex-1 overflow-y-auto pt-2 bg-bg-primary">
        {olderLoading && (
          <div className="flex items-center justify-center py-3">
            <svg className="animate-spin h-4 w-4 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        )}
        {messagesLoading && sorted.length === 0 && (
          <div className="flex items-center justify-center py-8">
            <svg className="animate-spin h-5 w-5 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        )}
        {messagesError && (
          <div className="flex items-center justify-center py-12">
            <div className="text-center">
              <p className="text-sm text-red-500 mb-2">{messagesError}</p>
              <button
                onClick={() => useChatStore.getState().loadMessages(conv.targetId, conv.sessionType)}
                className="text-xs text-primary-500 hover:text-primary-400 cursor-pointer"
              >
                Retry
              </button>
            </div>
          </div>
        )}
        {!messagesLoading && !messagesError && sorted.length === 0 && (
          <div className="flex items-center justify-center py-12">
            <p className="text-sm text-text-secondary">No messages yet. Say hello!</p>
          </div>
        )}
        {sorted.map((msg) => {
          const own = msg._localSent || String(msg.fromUid) === String(user?.uid)
          const isGroup = conv.sessionType === 'group'
          const senderProfile = !own && isGroup ? userProfiles[String(msg.fromUid)] : null
          return (
            <ChatBubble
              key={String(msg._clientId || msg.msgId || msg.seq)}
              msg={msg}
              isOwn={own}
              ownAvatar={user?.avatar}
              ownName={user?.nickname}
              otherAvatar={isGroup ? (senderProfile?.avatar || conv.avatar) : conv.avatar}
              otherName={isGroup ? (senderProfile?.nickname || String(msg.fromUid)) : (conv.memo || conv.nickname)}
              showSenderName={isGroup && !own}
              showStatus={!isGroup}
              onReply={handleReply}
              onForward={handleForward}
              onDelete={handleDelete}
              onAvatarClick={!own ? handleProfileClick : undefined}
              isPinned={pinnedMsgIds.has(String(msg.msgId))}
              onPinChange={refreshPinned}
            />
          )
        })}
        <div ref={bottomRef} className="h-2" />
      </div>

      <ChatInput
        onSend={handleSend}
        toUid={sessionType === 'single' ? targetId : undefined}
        sessionType={sessionType}
        targetId={targetId}
        replyTo={replyingTo}
        onCancelReply={() => setReplyingTo(null)}
      />

      <Modal
        open={forwardMsg !== null}
        onClose={() => { setForwardMsg(null); setForwardSelected(new Set()) }}
        title="Forward Message"
        size="sm"
      >
        {forwardMsg && (
          <div>
            <div className="bg-primary-50 dark:bg-primary-900/20 rounded-lg p-3 mb-3 border border-primary-100 dark:border-primary-900/30">
              <p className="text-xs text-text-secondary mb-1 font-medium">Preview</p>
              <p className="text-sm text-text-primary whitespace-pre-wrap break-words line-clamp-2">
                {formatForwardPreview(forwardMsg.content, forwardMsg.msgType)}
              </p>
            </div>
            {forwardTargets.length === 0 ? (
              <p className="text-sm text-text-secondary text-center py-4">No chats available to forward to</p>
            ) : (
              <>
                <div className="max-h-64 overflow-y-auto -mx-2 mb-3">
                  {forwardTargets.map((c) => {
                    const key = c.sessionType + ':' + c.targetId
                    const selected = forwardSelected.has(key)
                    return (
                      <button
                        key={key}
                        onClick={() => toggleForwardTarget(key)}
                        className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors cursor-pointer ${
                          selected ? 'bg-primary-50 dark:bg-primary-900/20' : 'hover:bg-gray-50 dark:hover:bg-gray-800'
                        }`}
                      >
                        <div className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 transition-colors ${
                          selected ? 'bg-primary-500 border-primary-500' : 'border-border-light'
                        }`}>
                          {selected && <svg className="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>}
                        </div>
                        <Avatar src={c.avatar} fallback={c.memo || c.nickname} size="sm" />
                        <div className="flex-1 min-w-0 text-left">
                          <div className="flex items-center gap-1.5">
                            <p className="text-sm font-medium text-text-primary truncate">{c.memo || c.nickname}</p>
                            {c.sessionType === 'group' && (
                              <span className="text-[10px] text-primary-500 bg-primary-50 dark:bg-primary-900/20 px-1 rounded shrink-0">Group</span>
                            )}
                          </div>
                        </div>
                      </button>
                    )
                  })}
                </div>
                <div className="flex items-center justify-between">
                  <button
                    onClick={() => { setForwardMsg(null); setForwardSelected(new Set()) }}
                    className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary hover:bg-surface-hover rounded-lg transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSendAll}
                    disabled={forwardSelected.size === 0}
                    className="px-4 py-2 bg-primary-500 text-white text-sm font-medium rounded-lg hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
                  >
                    Send{forwardSelected.size > 0 ? ` (${forwardSelected.size})` : ''}
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
