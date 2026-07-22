import { create } from 'zustand'
import type { Message } from '@/types/message'
import type { MessageStatus, MessageType } from '@/types/api'
import type { ConversationInfo } from '@/types/conversation'
import { convKey } from '@/utils/convKey'
import { uploadFile } from '@/utils/upload'
import { useAuthStore } from '@/stores/authStore'
import * as messageApi from '@/api/message'
import { getUserProfile } from '@/api/user'
import { toast } from '@/components/ui/Toast'

interface ChatState {
  conversations: ConversationInfo[]
  conversationsLoading: boolean
  activeConversation: ConversationInfo | null
  messages: Record<string, Message[]>
  messagesLoading: boolean
  messagesError: string | null
  olderLoading: boolean
  wsConnected: boolean
  onlineStatus: Record<string, boolean>
  userProfiles: Record<string, { nickname: string; avatar?: string; memo?: string; groupName?: string }>
  typingUsers: Record<string, number>
  _ws: WebSocket | null
  pendingScrollMsgId: string | null
  groupReadCounts: Record<string, number>

  loadConversations: () => Promise<void>
  refreshConversations: () => Promise<void>
  setActiveConversation: (conv: ConversationInfo | null) => void
  loadMessages: (targetId: string, sessionType: string) => Promise<void>
  refreshMessages: (targetId: string, sessionType: string) => Promise<void>
  loadOlderMessages: (targetId: string, sessionType: string) => Promise<void>
  sendMessage: (content: string, replyToMsgId?: string, msgType?: string) => Promise<Message | null>
  uploadAndSendFile: (file: File, meta?: Record<string, unknown>) => void
  appendMessage: (msg: Message) => void
  recallMessage: (msgId: string) => Promise<boolean>
  markRecalled: (msgId: string) => void
  markReadByPeer: (peerUid: string, readSeq?: number) => void
  incrementGroupReadCount: (msgId: string) => void
  setWsConnected: (connected: boolean) => void
  markRead: (targetId: string, sessionType: string, force?: boolean) => Promise<void>
  togglePin: (targetId: string, sessionType: string) => Promise<void>
  toggleDnd: (targetId: string, sessionType: string) => Promise<void>
  deleteMessage: (msgId: string) => Promise<void>
  deleteConversation: (targetId: string, sessionType: string) => Promise<void>
  forwardMessage: (toId: string, msgId: string, sessionType?: string) => Promise<Message | null>
  updateOnlineStatus: (statuses: Record<string, boolean>) => void
  updateUserProfiles: (profiles: Record<string, { nickname: string; avatar?: string; memo?: string }>) => void
  setTyping: (uid: string) => void
  setWsRef: (ws: WebSocket | null) => void
  loadUserProfile: (uid: string) => Promise<void>
  jumpToMessage: (targetId: string, sessionType: string, msgId: string) => Promise<void>
  reset: () => void
}

let conversationsPromise: Promise<ConversationInfo[]> | null = null
const messagesPromises: Record<string, Promise<Message[]> | undefined> = {}



export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  conversationsLoading: false,
  activeConversation: null,
  messages: {},
  messagesLoading: false,
  messagesError: null,
  olderLoading: false,
  pendingScrollMsgId: null,
  groupReadCounts: {},
  wsConnected: false,
  onlineStatus: {},
  userProfiles: {},
  typingUsers: {},
  _ws: null,

  loadConversations: async () => {
    if (conversationsPromise) return
    set({ conversationsLoading: true })
    conversationsPromise = messageApi.getConversations()
    try {
      const data = await conversationsPromise
      const merged = data ?? []

      const profiles: Record<string, { nickname: string; avatar?: string; memo?: string }> = {}
      const statuses: Record<string, boolean> = {}
      for (const c of merged) {
        if (c.sessionType === 'single' && c.nickname && c.nickname !== c.targetId) {
          profiles[c.targetId] = { nickname: c.nickname, avatar: c.avatar, memo: c.memo }
          if (c.online !== undefined) statuses[c.targetId] = c.online
        }
      }
      if (Object.keys(profiles).length > 0) {
        set((s) => ({
          userProfiles: { ...s.userProfiles, ...profiles },
          onlineStatus: { ...s.onlineStatus, ...statuses },
        }))
      }
      const active = get().activeConversation
      if (active) {
        const found = merged.find((c) => c.targetId === active.targetId && c.sessionType === active.sessionType)
        if (found && (found.nickname !== active.nickname || found.avatar !== active.avatar || found.online !== active.online || found.memo !== active.memo)) {
          set({
            conversations: merged,
            conversationsLoading: false,
            activeConversation: { ...active, nickname: found.nickname, avatar: found.avatar, online: found.online, memo: found.memo },
          })
          return
        }
      }
      set({ conversations: merged, conversationsLoading: false })
    } catch {
      set({ conversationsLoading: false })
    } finally {
      conversationsPromise = null
    }
  },

  refreshConversations: async () => {
    if (conversationsPromise) return
    conversationsPromise = messageApi.getConversations()
    try {
      const data = await conversationsPromise
      if (!data) return
      const current = get().conversations
      const active = get().activeConversation

      const merged = data.map((c) => {
        if (active && c.targetId === active.targetId && c.sessionType === active.sessionType) {
          const local = current.find((lc) => lc.targetId === c.targetId && lc.sessionType === c.sessionType)
          if (local && local.unreadCount < c.unreadCount) {
            return { ...c, unreadCount: local.unreadCount }
          }
        }
        return c
      })
      if (current.length !== merged.length || current.some((c, i) => c.lastMsg !== merged[i].lastMsg || c.unreadCount !== merged[i].unreadCount || c.isPinned !== merged[i].isPinned || c.lastMsgId !== merged[i].lastMsgId || c.online !== merged[i].online || c.nickname !== merged[i].nickname || c.avatar !== merged[i].avatar || c.memo !== merged[i].memo)) {
        const newActive = active
          ? merged.find((c) => c.targetId === active.targetId && c.sessionType === active.sessionType)
          : null

        const statuses: Record<string, boolean> = {}
        for (const c of merged) {
          if (c.sessionType === 'single' && c.online !== undefined) {
            statuses[c.targetId] = c.online
          }
        }
        set({
          conversations: merged,
          ...(Object.keys(statuses).length > 0 ? { onlineStatus: { ...get().onlineStatus, ...statuses } } : {}),
          ...(newActive && active && (newActive.online !== active.online || newActive.nickname !== active.nickname || newActive.avatar !== active.avatar || newActive.memo !== active.memo)
            ? { activeConversation: { ...active, online: newActive.online, nickname: newActive.nickname, avatar: newActive.avatar, memo: newActive.memo } }
            : {}),
        })
      }
    } catch {

    } finally {
      conversationsPromise = null
    }
  },

  setActiveConversation: (conv) => {
    const current = get().activeConversation
    if (
      conv &&
      current &&
      conv.targetId === current.targetId &&
      conv.sessionType === current.sessionType
    ) {
      return
    }
    if (!conv && !current) return
    set({ activeConversation: conv, messagesError: null })
    if (conv && conv.unreadCount > 0) {
      get().markRead(conv.targetId, conv.sessionType)
    }
    if (conv) {
      get().loadMessages(conv.targetId, conv.sessionType)
    }
  },

  loadMessages: async (targetId, sessionType) => {
    const key = convKey(targetId, sessionType)
    if (messagesPromises[key]) return
    const existing = get().messages[key]
    if (existing && existing.length > 0) {
      get().refreshMessages(targetId, sessionType)
      return
    }
    set({ messagesLoading: true, messagesError: null })
    messagesPromises[key] = (sessionType === 'group'
      ? messageApi.getGroupHistory(targetId)
      : messageApi.getHistory(targetId)) as Promise<Message[]>
    try {
      const msgs = await messagesPromises[key]!
      set((s) => ({
        messages: { ...s.messages, [key]: (msgs ?? []).sort((a: Message, b: Message) => (a.seq ?? 0) - (b.seq ?? 0)) },
        messagesLoading: false,
      }))
    } catch (err) {
      set({ messagesLoading: false, messagesError: err instanceof Error ? err.message : 'Failed to load messages' })
    } finally {
      delete messagesPromises[key]
    }
  },

  refreshMessages: async (targetId, sessionType) => {
    const key = convKey(targetId, sessionType)
    if (messagesPromises[key]) return
    const current = get().messages[key] ?? []
    if (current.length === 0) return
    if (current.some((m) => m._clientId && !m._failed && m.msgId === 0)) return
    messagesPromises[key] = (sessionType === 'group'
      ? messageApi.getGroupHistory(targetId)
      : messageApi.getHistory(targetId)) as Promise<Message[]>
    try {
      const msgs = await messagesPromises[key]!
      if (!msgs) return
      const existingMap = new Map<string, Message>()
      for (const m of current) existingMap.set(String(m.msgId), m)
      let changed = false
      for (const m of msgs) {
        const existing = existingMap.get(String(m.msgId))
        if (!existing) {
          existingMap.set(String(m.msgId), m)
          changed = true
        } else if (existing.isRecalled !== m.isRecalled || existing.status !== m.status) {
          const newStatus = (existing.status ?? 0) >= (m.status ?? 0) ? existing.status : m.status
          existingMap.set(String(m.msgId), { ...existing, isRecalled: m.isRecalled, status: newStatus })
          changed = true
        }
      }
      if (changed) {
        const merged = Array.from(existingMap.values()).sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
        set((s) => ({ messages: { ...s.messages, [key]: merged } }))

        const active = get().activeConversation
        if (active && active.targetId === targetId && active.sessionType === sessionType) {
          get().markRead(targetId, sessionType)
        }
      }
    } catch {

    } finally {
      delete messagesPromises[key]
    }
  },

  loadOlderMessages: async (targetId, sessionType) => {
    const key = convKey(targetId, sessionType)
    const current = get().messages[key] ?? []
    const oldest = current.length > 0 ? Math.min(...current.map((m) => m.seq ?? 0)) : null
    if (oldest == null || oldest <= 1) return
    set({ olderLoading: true })
    try {
      const older = sessionType === 'group'
        ? await messageApi.getGroupHistory(targetId, oldest)
        : await messageApi.getHistory(targetId, oldest)
      if (!older?.length) return
      set((s) => {
        const existing = s.messages[key] ?? []
        const existingIds = new Set(existing.map((m) => String(m.msgId)))
        const newMsgs = older.filter((m) => !existingIds.has(String(m.msgId)))
            .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
        return { messages: { ...s.messages, [key]: [...newMsgs, ...existing] } }
      })
    } finally {
      set({ olderLoading: false })
    }
  },

  sendMessage: async (content, replyToMsgId, msgType) => {
    const conv = get().activeConversation
    if (!conv) return null

    const key = convKey(conv.targetId, conv.sessionType)
    const tempSeq = Date.now()
    const clientId = crypto.randomUUID()
    const type = (msgType || 'TEXT') as MessageType

    const tempMsg: Message = {
      msgId: 0,
      sessionType: conv.sessionType,
      fromUid: 0,
      toId: conv.targetId,
      msgType: type,
      content,
      status: 0,
      isRecalled: 0,
      isForwarded: 0,
      forwardFromUid: null,
      replyToMsgId: replyToMsgId || null,
      mentionedUids: null,
      seq: tempSeq,
      createdAt: new Date().toISOString(),
      _clientId: clientId,
      _localSent: true,
    }

    set((s) => ({
      messages: {
        ...s.messages,
        [key]: [...(s.messages[key] ?? []), tempMsg],
      },
    }))

    try {
      const msg =
        replyToMsgId
          ? conv.sessionType === 'group'
            ? await messageApi.sendGroupMessage(conv.targetId, content, type, replyToMsgId)
            : await messageApi.replyMessage(conv.targetId, replyToMsgId, content)
          : conv.sessionType === 'group'
            ? await messageApi.sendGroupMessage(conv.targetId, content, type)
            : await messageApi.sendMessage(conv.targetId, content, type)

      set((s) => {
        const existing = s.messages[key] ?? []
        const msgIdStr = String(msg.msgId)
        let msgs: Message[]
        if (existing.some((m) => String(m.msgId) === msgIdStr)) {
          msgs = existing.filter((m) => !(m._clientId === clientId))
        } else {
          msgs = existing.map((m) =>
            m._clientId === clientId ? { ...msg, _clientId: m._clientId } : m,
          )
        }
        return {
          messages: { ...s.messages, [key]: msgs },
          conversations: s.conversations.map((c) =>
            c.targetId === conv.targetId && c.sessionType === conv.sessionType
              ? { ...c, lastMsg: msg.content, lastMsgTime: msg.createdAt, lastMsgId: msgIdStr, updatedAt: msg.createdAt }
              : c,
          ),
        }
      })
      return msg
    } catch (err) {
      set((s) => {
        const msgs = (s.messages[key] ?? []).map((m) =>
          m._clientId === clientId
            ? { ...m, _failed: true as const }
            : m,
        )
        return { messages: { ...s.messages, [key]: msgs } }
      })
      toast(err instanceof Error ? err.message : 'Send failed', 'error')
      return null
    }
  },

  uploadAndSendFile: (file: File, meta?: Record<string, unknown>) => {
    const conv = get().activeConversation
    if (!conv) return

    const key = convKey(conv.targetId, conv.sessionType)
    const clientId = crypto.randomUUID()
    const isImage = file.type.startsWith('image/')
    const isVideo = file.type.startsWith('video/')
    const isVoice = file.type.startsWith('audio/')
    const msgType: MessageType = isImage ? 'IMAGE' : isVideo ? 'VIDEO' : isVoice ? 'VOICE' : 'FILE'
    const placeholder = isImage ? '[Image]' : isVideo ? '[Video]' : isVoice ? '[Voice]' : `[File: ${file.name}]`
    const controller = new AbortController()

    const tempMsg: Message = {
      msgId: 0,
      sessionType: conv.sessionType,
      fromUid: 0,
      toId: conv.targetId,
      msgType,
      content: placeholder,
      status: 0,
      isRecalled: 0,
      isForwarded: 0,
      forwardFromUid: null,
      replyToMsgId: null,
      mentionedUids: null,
      seq: Date.now(),
      createdAt: new Date().toISOString(),
      _clientId: clientId,
      _localSent: true,
      _uploading: true,
      _uploadProgress: 0,
    }

    set((s) => ({
      messages: {
        ...s.messages,
        [key]: [...(s.messages[key] ?? []), tempMsg],
      },
    }))

    uploadFile(file, {
      meta,
      signal: controller.signal,
      onProgress: (pct) => {
        set((s) => ({
          messages: {
            ...s.messages,
            [key]: (s.messages[key] ?? []).map((m) =>
              m._clientId === clientId ? { ...m, _uploadProgress: pct } : m,
            ),
          },
        }))
      },
    })
      .then((result) => {
        set((s) => ({
          messages: {
            ...s.messages,
            [key]: (s.messages[key] ?? []).map((m) =>
              m._clientId === clientId
                ? { ...m, content: result.content, _uploading: false, _uploadProgress: undefined }
                : m,
            ),
          },
        }))

        if (conv.sessionType === 'group') {
          messageApi.sendGroupMessage(conv.targetId, result.content, result.msgType)
            .then((msg) => {
              set((s) => {
                const merged = (s.messages[key] ?? []).map((m) =>
                  m._clientId === clientId ? { ...msg, _clientId: m._clientId } : m,
                )
                const seen = new Set<string>()
                const deduped = merged.filter((m) => {
                  const id = String(m.msgId)
                  if (id === '0') return true
                  if (seen.has(id)) return false
                  seen.add(id)
                  return true
                })
                return { messages: { ...s.messages, [key]: deduped } }
              })
            })
            .catch(() => {
              set((s) => ({
                messages: {
                  ...s.messages,
                  [key]: (s.messages[key] ?? []).map((m) =>
                    m._clientId === clientId ? { ...m, _failed: true as const } : m,
                  ),
                },
              }))
              toast('Send failed', 'error')
            })
        } else {
          messageApi.sendMessage(conv.targetId, result.content, result.msgType)
            .then((msg) => {
              set((s) => {
                const merged = (s.messages[key] ?? []).map((m) =>
                  m._clientId === clientId ? { ...msg, _clientId: m._clientId } : m,
                )
                const seen = new Set<string>()
                const deduped = merged.filter((m) => {
                  const id = String(m.msgId)
                  if (id === '0') return true
                  if (seen.has(id)) return false
                  seen.add(id)
                  return true
                })
                return { messages: { ...s.messages, [key]: deduped } }
              })
            })
            .catch((err: unknown) => {
              set((s) => ({
                messages: {
                  ...s.messages,
                  [key]: (s.messages[key] ?? []).map((m) =>
                    m._clientId === clientId ? { ...m, _failed: true as const } : m,
                  ),
                },
              }))
              toast(err instanceof Error ? err.message : 'Upload failed', 'error')
            })
        }
      })
            .catch((err: unknown) => {
              set((s) => ({
                messages: {
                  ...s.messages,
                  [key]: (s.messages[key] ?? []).map((m) =>
                    m._clientId === clientId ? { ...m, _failed: true as const } : m,
                  ),
                },
              }))
              toast(err instanceof Error ? err.message : 'Send failed', 'error')
            })
  },

  appendMessage: (msg) => {
    const targetId = msg.sessionType === 'group' ? msg.toId : String(msg.fromUid)
    const key = convKey(targetId, msg.sessionType)
    const active = get().activeConversation
    const isCurrentChat = active?.targetId === (msg.sessionType === 'group' ? msg.toId : String(msg.fromUid))
    const existingConv = get().conversations.find(c => c.targetId === targetId && c.sessionType === msg.sessionType)
    const convExists = existingConv != null

    set((s) => {
      const existing = s.messages[key] ?? []
      const dupIdx = existing.findIndex((m) => String(m.msgId) === String(msg.msgId))
      if (dupIdx >= 0) {
        const updated = [...existing]
        const prev = existing[dupIdx]
        updated[dupIdx] = { ...prev, ...msg, _localSent: prev._localSent, _failed: prev._failed, status: Math.max(prev.status ?? 0, msg.status ?? 0) as MessageStatus }
        return { messages: { ...s.messages, [key]: updated } }
      }


      let conversations = s.conversations
      const convIdx = conversations.findIndex((c) => c.targetId === targetId && c.sessionType === msg.sessionType)
      if (convIdx >= 0) {
        conversations = conversations.map((c) =>
          c.targetId === targetId && c.sessionType === msg.sessionType
            ? { ...c, lastMsg: msg.content, lastMsgTime: msg.createdAt, lastMsgId: String(msg.msgId), updatedAt: msg.createdAt }
            : c,
        )
      } else {
        conversations = [
          {
            targetId,
            sessionType: msg.sessionType,
            lastMsg: msg.content,
            lastMsgTime: msg.createdAt,
            lastMsgId: String(msg.msgId),
            unreadCount: 0,
            isPinned: 0,
            updatedAt: msg.createdAt,
            nickname: msg.sessionType === 'group' ? targetId : '...',
            avatar: '',
          } as ConversationInfo,
          ...conversations,
        ]
      }


      let msgs = [...existing, msg]
      if (active && isCurrentChat) {
        const myUid = useAuthStore.getState().user?.uid
        conversations = conversations.map((c) =>
          c.targetId === targetId && c.sessionType === msg.sessionType
            ? { ...c, unreadCount: 0 } : c,
        )
        msgs = msgs.map((m) => (m.status < 2 && (!myUid || String(m.fromUid) !== myUid) ? { ...m, status: 2 as const } : m))
      } else {
        conversations = conversations.map((c) =>
          c.targetId === targetId && c.sessionType === msg.sessionType
            ? { ...c, unreadCount: c.unreadCount + 1 } : c,
        )
      }
      return { messages: { ...s.messages, [key]: msgs }, conversations }
    })

    if (isCurrentChat && active) {
      get().markRead(active.targetId, active.sessionType, true)
    }

    if (!convExists && msg.sessionType === 'group') {
      get().refreshConversations()
    }
  },

  markRecalled: (msgId) => {
    set((s) => {
      const next: Record<string, Message[]> = {}
      for (const [key, msgs] of Object.entries(s.messages)) {
        next[key] = msgs.map((m) =>
          String(m.msgId) === msgId ? { ...m, isRecalled: 1 } : m,
        )
      }
      return { messages: next }
    })
  },

  markReadByPeer: (peerUid, readSeq) => {
    const key = convKey(peerUid, 'single')
    const myUid = useAuthStore.getState().user?.uid
    set((s) => {
      const msgs = (s.messages[key] ?? []).map((m) =>
        m.status < 2
          && (!myUid || String(m.fromUid) === myUid)
          && readSeq != null
          && (m.seq ?? 0) <= readSeq
          ? { ...m, status: 2 as const }
          : m,
      )
      return s.messages[key] === msgs ? s : { messages: { ...s.messages, [key]: msgs } }
    })
  },

  incrementGroupReadCount: (msgId) => {
    set((s) => ({
      groupReadCounts: { ...s.groupReadCounts, [msgId]: (s.groupReadCounts[msgId] ?? 0) + 1 },
    }))
  },

  setWsConnected: (connected) => {
    set({ wsConnected: connected })
  },

  recallMessage: async (msgId) => {
    try {
      const recalled = await messageApi.recallMessage(msgId)
      set((s) => {
        const next: Record<string, Message[]> = {}
        let recalledKey = ''

        for (const [key, msgs] of Object.entries(s.messages)) {
          next[key] = msgs.map((m) => {
            if (String(m.msgId) === msgId) {
              recalledKey = key
              return { ...recalled, isRecalled: 1 }
            }
            return m
          })
        }


        if (recalledKey) {
          const prev = (next[recalledKey] ?? [])
            .filter((m) => m.isRecalled !== 1 && String(m.msgId) !== msgId)
            .sort((a, b) => (b.seq ?? 0) - (a.seq ?? 0))[0]

          const [sessionType, targetId] = recalledKey.split(':')
          const updatedConversations = s.conversations.map((c) =>
            c.sessionType === sessionType && c.targetId === targetId
              ? {
                  ...c,
                  lastMsg: prev ? prev.content : null,
                  lastMsgId: prev ? String(prev.msgId) : null,
                  lastMsgTime: prev ? prev.createdAt : null,
                }
              : c,
          )
          return { messages: next, conversations: updatedConversations }
        }

        return { messages: next }
      })
      return true
    } catch {
      return false
    }
  },

  markRead: async (targetId, sessionType, force) => {
    if (!force && (document.visibilityState !== 'visible' || !document.hasFocus())) return
    try {
      if (sessionType === 'group') {
        await messageApi.markGroupAsRead(targetId)
      } else {
        await messageApi.markAsRead(targetId)
      }
      const myUid = useAuthStore.getState().user?.uid
      set((s) => {
        const key = convKey(targetId, sessionType)
        const updatedMessages = sessionType === 'single'
          ? (s.messages[key] ?? []).map((m) =>
              m.status < 2 && (!myUid || String(m.fromUid) !== myUid) ? { ...m, status: 2 as const } : m,
            )
          : (s.messages[key] ?? [])
        return {
          conversations: s.conversations.map((c) =>
            c.targetId === targetId && c.sessionType === sessionType
              ? { ...c, unreadCount: 0 }
              : c,
          ),
          messages: { ...s.messages, [key]: updatedMessages },
        }
      })
    } catch (e) {
      console.error('[markRead] failed:', e)
    }
  },

  togglePin: async (targetId, sessionType) => {
    const current = get().conversations.find(
      (c) => c.targetId === targetId && c.sessionType === sessionType,
    )
    if (!current) return
    const newPinned = current.isPinned !== 1

    set((s) => ({
      conversations: s.conversations.map((c) =>
        c.targetId === targetId && c.sessionType === sessionType
          ? { ...c, isPinned: newPinned ? 1 : 0 }
          : c,
      ),
      activeConversation:
        s.activeConversation?.targetId === targetId &&
        s.activeConversation?.sessionType === sessionType
          ? { ...s.activeConversation, isPinned: newPinned ? 1 : 0 }
          : s.activeConversation,
    }))
    try {
      if (sessionType === 'group') {
        await messageApi.pinGroupConversation(targetId, newPinned)
      } else {
        await messageApi.pinConversation(targetId, newPinned)
      }
    } catch {

      set((s) => ({
        conversations: s.conversations.map((c) =>
          c.targetId === targetId && c.sessionType === sessionType
            ? { ...c, isPinned: current.isPinned }
            : c,
        ),
        activeConversation:
          s.activeConversation?.targetId === targetId &&
          s.activeConversation?.sessionType === sessionType
            ? { ...s.activeConversation, isPinned: current.isPinned }
            : s.activeConversation,
      }))
    }
  },

  toggleDnd: async (targetId, sessionType) => {
    if (sessionType === 'group') return
    const current = get().conversations.find(
      (c) => c.targetId === targetId && c.sessionType === sessionType,
    )
    if (!current) return
    const newDnd = !current.dnd
    set((s) => ({
      conversations: s.conversations.map((c) =>
        c.targetId === targetId && c.sessionType === sessionType
          ? { ...c, dnd: newDnd }
          : c,
      ),
      activeConversation:
        s.activeConversation?.targetId === targetId &&
        s.activeConversation?.sessionType === sessionType
          ? { ...s.activeConversation, dnd: newDnd }
          : s.activeConversation,
    }))
    try {
      await messageApi.setConversationDnd(targetId, newDnd)
    } catch {
      set((s) => ({
        conversations: s.conversations.map((c) =>
          c.targetId === targetId && c.sessionType === sessionType
            ? { ...c, dnd: current.dnd }
            : c,
        ),
        activeConversation:
          s.activeConversation?.targetId === targetId &&
          s.activeConversation?.sessionType === sessionType
            ? { ...s.activeConversation, dnd: current.dnd }
            : s.activeConversation,
      }))
    }
  },

  deleteMessage: async (msgId) => {

    set((s) => {
      const next: Record<string, Message[]> = {}
      for (const [key, msgs] of Object.entries(s.messages)) {
        next[key] = msgs.filter((m) => String(m.msgId) !== msgId)
      }

      let updatedConversations = s.conversations
      for (const [key, msgs] of Object.entries(s.messages)) {
        const targetMsg = msgs.find((m) => String(m.msgId) === msgId)
        if (!targetMsg) continue
        const [sessionType, targetId] = key.split(':')
        const conv = s.conversations.find((c) => c.targetId === targetId && c.sessionType === sessionType)
        if (!conv || conv.lastMsgId !== msgId) break
        const prev = [...msgs]
          .filter((m) => String(m.msgId) !== msgId && m.isRecalled !== 1)
          .sort((a, b) => (b.seq ?? 0) - (a.seq ?? 0))[0]
        updatedConversations = s.conversations.map((c) =>
          c.targetId === targetId && c.sessionType === sessionType
            ? {
                ...c,
                lastMsg: prev ? prev.content : null,
                lastMsgId: prev ? String(prev.msgId) : null,
                lastMsgTime: prev ? prev.createdAt : null,
              }
            : c,
        )
        break
      }
      return { messages: next, conversations: updatedConversations }
    })
    try {
      await messageApi.deleteMessage(msgId)
    } catch {

      get().refreshConversations()
    }
  },

  deleteConversation: async (targetId, sessionType) => {
    try {
      if (sessionType === 'group') {
        await messageApi.deleteGroupConversation(targetId)
      } else {
        await messageApi.deleteConversation(targetId)
      }
      set((s) => {
        const key = convKey(targetId, sessionType)
        const { [key]: _, ...restMessages } = s.messages
        const active = s.activeConversation
        return {
          conversations: s.conversations.filter((c) => !(c.targetId === targetId && c.sessionType === sessionType)),
          messages: restMessages,
          activeConversation:
            active && active.targetId === targetId && active.sessionType === sessionType
              ? null
              : active,
        }
      })
    } catch {

    }
  },

  forwardMessage: async (toId, msgId, sessionType) => {
    const isGroup = sessionType === 'group'
    try {
      const msg = isGroup
        ? await messageApi.forwardGroupMessage(toId, msgId)
        : await messageApi.forwardMessage(toId, msgId)
      const key = convKey(toId, isGroup ? 'group' : 'single')
      set((s) => {
        const existing = s.messages[key] ?? []
        const conv = s.conversations.find((c) => c.targetId === toId && c.sessionType === (isGroup ? 'group' : 'single'))
        let conversations = s.conversations
        if (conv) {
          conversations = conversations.map((c) =>
            c.targetId === toId && c.sessionType === (isGroup ? 'group' : 'single')
              ? { ...c, lastMsg: msg.content, lastMsgTime: msg.createdAt, lastMsgId: String(msg.msgId), updatedAt: msg.createdAt }
              : c,
          )
        }
        return {
          messages: { ...s.messages, [key]: [...existing, msg] },
          conversations,
        }
      })
      return msg
    } catch {
      return null
    }
  },

  updateOnlineStatus: (statuses) => {
    set((s) => ({
      onlineStatus: { ...s.onlineStatus, ...statuses },
    }))
  },

  updateUserProfiles: (profiles) => {
    set((s) => ({
      userProfiles: { ...s.userProfiles, ...profiles },
    }))
  },

  setTyping: (uid) => {
    set((s) => {
      const now = Date.now()
      const next = { ...s.typingUsers }

      for (const [k, ts] of Object.entries(next)) {
        if (now - ts > 6000) delete next[k]
      }
      next[uid] = now
      return { typingUsers: next }
    })
  },

  setWsRef: (ws) => {
    set({ _ws: ws })
  },

  loadUserProfile: async (uid) => {
    if (!uid || uid === '0') return
    if (get().userProfiles[uid]?.nickname) return
    try {
      const user = await getUserProfile(uid)
      if (user) {
        set((s) => ({
          userProfiles: { ...s.userProfiles, [uid]: { nickname: user.nickname, avatar: user.avatar } },
        }))
      }
    } catch (e) {
      console.error('[loadUserProfile] failed for uid:', uid, e)
    }
  },

  jumpToMessage: async (targetId, sessionType, msgId) => {
    const key = convKey(targetId, sessionType)
    const existing = get().messages[key]
    const alreadyLoaded = existing?.some((m) => String(m.msgId) === String(msgId))

    if (!alreadyLoaded) {
      try {
        const contextMsgs = await messageApi.getMessageContext(msgId)
        if (contextMsgs?.length) {
          set((s) => {
            const current = s.messages[key] ?? []
            const existingIds = new Set(current.map((m) => String(m.msgId)))
            const newMsgs = contextMsgs.filter((m) => !existingIds.has(String(m.msgId)))
            return {
              messages: { ...s.messages, [key]: [...newMsgs, ...current].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0)) },
            }
          })
        }
      } catch (e) {
        console.error('[jumpToMessage] failed to load context:', e)
      }
    }

    set({ pendingScrollMsgId: String(msgId) })
  },

  reset: () => {

    conversationsPromise = null
    for (const k of Object.keys(messagesPromises)) delete messagesPromises[k]
    set({
      conversations: [],
      conversationsLoading: false,
      activeConversation: null,
      messages: {},
      messagesLoading: false,
      messagesError: null,
      olderLoading: false,
  pendingScrollMsgId: null,
  groupReadCounts: {},
      wsConnected: false,
      onlineStatus: {},
      userProfiles: {},
      typingUsers: {},
      _ws: null,
    })
  },
}))
