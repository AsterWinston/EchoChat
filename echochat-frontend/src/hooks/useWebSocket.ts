import { useEffect, useRef, useCallback } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { notify as desktopNotify } from '@/utils/notify'
import type { Message } from '@/types/message'
import type { Notification } from '@/types/notification'

type WsMessage = {
  type: string
  data: unknown
  timestamp: number
}

export function useWebSocket() {
  const token = useAuthStore((s) => s.accessToken)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const pingTimer = useRef<ReturnType<typeof setInterval> | undefined>(undefined)
  const retryCount = useRef(0)
  const cleaningUp = useRef(false)

  const sendSyncRequest = useCallback(() => {
    const store = useChatStore.getState()
    const ws = store._ws || wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    const sessions: Array<{ sessionType: string; targetId: string; maxSeq: number }> = []
    for (const [key, msgs] of Object.entries(store.messages)) {
      if (msgs.length === 0) continue
      const [sessionType, targetId] = key.split(':')
      const maxSeq = Math.max(...msgs.map(m => m.seq ?? 0))
      if (maxSeq > 0) {
        sessions.push({ sessionType, targetId, maxSeq })
      }
    }
    if (sessions.length > 0) {
      ws.send(JSON.stringify({ type: 'sync', data: { sessions } }))
    } else {
      ws.send(JSON.stringify({ type: 'sync' }))
    }
  }, [])

  const syncMissedMessages = useCallback(async () => {
    try {
      const { syncMessages } = await import('@/api/message')
      const store = useChatStore.getState()
      for (const [key, msgs] of Object.entries(store.messages)) {
        if (msgs.length === 0) continue
        const [sessionType, targetId] = key.split(':')
        const maxSeq = Math.max(...msgs.map(m => m.seq ?? 0))
        if (maxSeq <= 0) continue
        try {
          const newMsgs = await syncMessages(sessionType, targetId, maxSeq)
          if (newMsgs && newMsgs.length > 0) {
            const existingMap = new Map<string, Message>()
            const current = useChatStore.getState().messages[key] ?? []
            for (const m of current) existingMap.set(String(m.msgId), m)
            for (const m of newMsgs) {
              if (!existingMap.has(String(m.msgId))) {
                existingMap.set(String(m.msgId), m)
              }
            }
            const merged = Array.from(existingMap.values()).sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
            useChatStore.setState((s) => ({ messages: { ...s.messages, [key]: merged } }))
          }
        } catch { }
      }
    } catch { }
  }, [])

  const connect = useCallback(() => {
    if (!token || wsRef.current?.readyState === WebSocket.OPEN) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname
    const url = `${protocol}//${host}:9010/ws?token=${encodeURIComponent(token)}`

    try {
      const ws = new WebSocket(url)
      wsRef.current = ws
      useChatStore.getState().setWsRef(ws)

      ws.onopen = () => {
        retryCount.current = 0
        useChatStore.getState().setWsConnected(true)
        sendSyncRequest()
        setTimeout(() => {
          useChatStore.getState().refreshConversations()
        }, 1500)
        syncMissedMessages()
        pingTimer.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' }))
          }
        }, 30000)
      }

      ws.onmessage = (event) => {
        try {
          const payload: WsMessage = JSON.parse(event.data)
          handleWsMessage(payload)
        } catch { }
      }

      ws.onclose = () => {
        useChatStore.getState().setWsConnected(false)
        useChatStore.getState().setWsRef(null)
        clearInterval(pingTimer.current)
        wsRef.current = null
        if (!cleaningUp.current) {
          scheduleReconnect()
        }
      }

      ws.onerror = () => {
        ws.close()
      }
    } catch {
      console.warn('[WS] WebSocket constructor failed, scheduling reconnect')
      scheduleReconnect()
      return
    }
  }, [token])

  const scheduleReconnect = useCallback(() => {
    const delay = Math.min(3000 * Math.pow(2, retryCount.current), 60000)
    retryCount.current++
    reconnectTimer.current = setTimeout(() => {
      if (useAuthStore.getState().isAuthenticated) {
        connect()
      }
    }, delay)
  }, [connect])

  useEffect(() => {
    if (isAuthenticated && token) {
      cleaningUp.current = false
      const t = setTimeout(() => connect(), 200)
      return () => {
        clearTimeout(t)
        cleaningUp.current = true
        clearTimeout(reconnectTimer.current)
        clearInterval(pingTimer.current)
        wsRef.current?.close()
        wsRef.current = null
        useChatStore.getState().setWsRef(null)
        useChatStore.getState().setWsConnected(false)
      }
    }
    return () => {
      cleaningUp.current = true
      clearTimeout(reconnectTimer.current)
      clearInterval(pingTimer.current)
      wsRef.current?.close()
      wsRef.current = null
      useChatStore.getState().setWsRef(null)
      useChatStore.getState().setWsConnected(false)
    }
  }, [isAuthenticated, token, connect])
}

function handleWsMessage(payload: WsMessage) {
  const store = useChatStore.getState()

  switch (payload.type) {
    case 'message':
    case 'message_mentioned': {
      const raw = payload.data
      const msg: Message = typeof raw === 'string' ? JSON.parse(raw) as Message : raw as Message
      store.appendMessage(msg)
      if (msg.sessionType === 'group') {
        store.loadUserProfile(String(msg.fromUid))
      }

      const myUid = useAuthStore.getState().user?.uid
      if (myUid && String(msg.fromUid) !== String(myUid)) {
        const targetId = msg.sessionType === 'group' ? msg.toId : String(msg.fromUid)
        const conv = store.conversations.find(c => c.targetId === targetId && c.sessionType === msg.sessionType)
        if (!conv?.dnd) {
          const profile = store.userProfiles[String(msg.fromUid)]
          const senderName = (profile?.memo || profile?.nickname) || 'User'
          const preview = msg.content.length > 50 ? msg.content.slice(0, 50) + '...' : msg.content
          const path = msg.sessionType === 'group' ? `/chat/group/${targetId}` : `/chat/${targetId}`
          desktopNotify(senderName, preview, path)
        }
      }
      break
    }
    case 'recall': {
      const data = payload.data as { msgId: number }
      store.markRecalled(String(data.msgId))
      break
    }
    case 'read': {
      const data = payload.data as { uid: string; seq: number }
      store.markReadByPeer(data.uid, data.seq)
      break
    }
    case 'group_read': {
      const data = payload.data as { msgId: string; readerUid: string }
      store.incrementGroupReadCount(data.msgId)
      break
    }
    case 'typing': {
      const data = payload.data as { uid: number | string }
      store.setTyping(String(data.uid))
      break
    }
    case 'notification': {
      const raw = payload.data
      const data = raw as Record<string, unknown>
      const notification: Notification = {
        id: data.id as string | number,
        uid: 0,
        type: String(data.type || 'system'),
        title: String(data.title || ''),
        content: data.content as string | null,
        relatedId: null,
        isRead: 0,
        createdAt: String(data.createdAt || new Date().toISOString()),
      }
      useNotificationStore.getState().appendNotification(notification)
      desktopNotify(notification.title, notification.content || '', '/notifications')
      break
    }
    case 'pong':
      break
    default:
      break
  }
}
