import { useState, useEffect, useCallback, useRef, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import * as searchApi from '@/api/search'
import { formatTime } from '@/utils/time'
import Avatar from '@/components/ui/Avatar'
import Tabs from '@/components/ui/Tabs'
import type { GroupSearchResult, MessageSearchResult, UserSearchResult } from '@/types/search'

type Tab = 'messages' | 'users' | 'groups'

function formatContent(raw: string): string {
  if (!raw) return ''
  if (raw.startsWith('[')) {
    try {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr) && arr.length > 0) return '[Image]'
    } catch {  }
  }
  if (raw.startsWith('{')) {
    try {
      const obj = JSON.parse(raw)
      if (obj.mimeType?.startsWith('video/')) return '[Video]'
      if (obj.mimeType?.startsWith('audio/')) return '[Voice]'
      return '[File]'
    } catch {  }
  }
  return raw.length > 120 ? raw.slice(0, 120) + '…' : raw
}



export default function SearchPage() {
  const [query, setQuery] = useState('')
  const [activeTab, setActiveTab] = useState<Tab>('messages')
  const [messages, setMessages] = useState<MessageSearchResult[]>([])
  const [users, setUsers] = useState<UserSearchResult[]>([])
  const [groups, setGroups] = useState<GroupSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searched, setSearched] = useState(false)

  const currentUid = useAuthStore((s) => s.user?.uid)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)

  const trimmedQuery = query.trim()

  const doSearch = useCallback(async (q: string, tab: Tab) => {
    if (!q) {
      setMessages([])
      setUsers([])
      setGroups([])
      setSearched(false)
      return
    }
    setLoading(true)
    setError('')
    setSearched(true)
    try {
      if (tab === 'messages') {
        const data = await searchApi.searchMessages(q)
        setMessages(data ?? [])
        const uids = new Set<string>()
        for (const m of data ?? []) {
          uids.add(String(m.fromUid))
          if (m.sessionType !== 'group') uids.add(String(m.toId))
        }
        for (const uid of uids) loadUserProfile(uid)
      } else if (tab === 'users') {
        const data = await searchApi.searchUsers(q)
        setUsers(data ?? [])
        for (const u of data ?? []) loadUserProfile(u.uid)
      } else {
        const data = await searchApi.searchGroups(q)
        setGroups(data ?? [])
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Search failed')
    } finally {
      setLoading(false)
    }
  }, [loadUserProfile])

  useEffect(() => {
    const timer = setTimeout(() => {
      doSearch(trimmedQuery, activeTab)
    }, 300)
    return () => clearTimeout(timer)
  }, [trimmedQuery, activeTab, doSearch])

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const getParticipantName = useCallback((uid: string) => {
    const profile = userProfiles[uid]
    return profile?.nickname || `User ${uid.slice(-6)}`
  }, [userProfiles])

  const getDirection = useCallback((msg: MessageSearchResult) => {
    const isFromMe = String(msg.fromUid) === String(currentUid)
    if (msg.sessionType === 'group') {
      const sender = isFromMe ? 'You' : (msg.fromName || getParticipantName(String(msg.fromUid)))
      const target = msg.toName || `Group ${String(msg.toId).slice(-6)}`
      return `${sender} › ${target}`
    }
    if (isFromMe) {
      return `You › ${getParticipantName(String(msg.toId))}`
    }
    return `${getParticipantName(String(msg.fromUid))} › You`
  }, [currentUid, getParticipantName])

  const getTargetPath = useCallback((msg: MessageSearchResult) => {
    const isFromMe = String(msg.fromUid) === String(currentUid)
    if (msg.sessionType === 'group') {
      return `/chat/group/${msg.toId}?msg=${msg.msgId}`
    }
    return `/chat/${isFromMe ? msg.toId : msg.fromUid}?msg=${msg.msgId}`
  }, [currentUid])

  const tabResults = useMemo(() => {
    const map: Record<Tab, { items: MessageSearchResult[] | UserSearchResult[] | GroupSearchResult[] }> = {
      messages: { items: messages },
      users: { items: users },
      groups: { items: groups },
    }
    return map[activeTab]
  }, [activeTab, messages, users, groups])

  const tabs = [
    { key: 'messages', label: 'Messages', badge: searched ? messages.length : undefined },
    { key: 'users', label: 'Users', badge: searched ? users.length : undefined },
    { key: 'groups', label: 'Groups', badge: searched ? groups.length : undefined },
  ]

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-5 py-4 bg-surface border-b border-border-light shrink-0">
        <div className="relative">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-text-secondary pointer-events-none"
            viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search messages and users…"
            className="w-full h-11 pl-10 pr-10 bg-surface-active rounded-xl text-sm text-text-primary placeholder:text-text-secondary
                       border border-transparent focus:border-primary-400 focus:bg-surface focus:outline-none
                       transition-colors duration-200"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 w-5 h-5 flex items-center justify-center
                         rounded-full bg-surface-active text-text-secondary hover:bg-border-light transition-colors cursor-pointer"
            >
              <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>

        <div className="mt-3 -mb-1">
          <Tabs items={tabs} activeKey={activeTab} onChange={(k) => setActiveTab(k as Tab)} />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto bg-bg-primary">
        <div className="h-7 shrink-0 flex items-center justify-center bg-surface border-b border-border-light sticky top-0 z-10">
          {loading && (
            <span className="inline-flex items-center gap-1.5 text-xs text-text-secondary">
              <svg className="animate-spin h-3 w-3 text-primary-500" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Searching…
            </span>
          )}
        </div>

        {!searched && !loading && (
          <div className="flex items-center justify-center py-20">
            <div className="text-center text-text-secondary">
              <div className="w-20 h-20 rounded-2xl bg-primary-100 flex items-center justify-center mx-auto mb-4">
                <svg className="w-10 h-10 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" />
                  <path d="m21 21-4.35-4.35" />
                </svg>
              </div>
              <h3 className="text-lg font-medium text-text-primary mb-1">Search</h3>
              <p className="text-sm">Search messages, users, and groups</p>
            </div>
          </div>
        )}

        {error && !loading && (
          <div className="flex items-center justify-center py-16">
            <div className="text-center">
              <p className="text-sm text-red-500">{error}</p>
            </div>
          </div>
        )}

        {searched && !loading && !error && tabResults.items.length === 0 && (
          <div className="flex items-center justify-center py-16">
            <div className="text-center text-text-secondary">
              <div className="w-16 h-16 rounded-2xl bg-surface-active flex items-center justify-center mx-auto mb-3">
                <svg className="w-8 h-8 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" />
                  <path d="m21 21-4.35-4.35" />
                </svg>
              </div>
              <p className="text-sm">No {activeTab} found</p>
              <p className="text-xs mt-1">Try a different keyword</p>
            </div>
          </div>
        )}

        {activeTab === 'messages' && messages.length > 0 && (
          <div className="divide-y divide-border-light">
            {messages.map((msg) => (
              <MessageRow
                key={msg.msgId}
                msg={msg}
                direction={getDirection(msg)}
                navigateTo={getTargetPath(msg)}
                onNavigate={navigate}
              />
            ))}
          </div>
        )}

        {activeTab === 'users' && users.length > 0 && (
          <div className="divide-y divide-border-light">
            {users.map((user) => (
              <UserRow
                key={user.uid}
                user={user}
                onNavigate={navigate}
              />
            ))}
          </div>
        )}

        {activeTab === 'groups' && groups.length > 0 && (
          <div className="divide-y divide-border-light">
            {groups.map((group) => (
              <GroupRow
                key={group.gid}
                group={group}
                onNavigate={navigate}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}



function MessageRow({
  msg,
  direction,
  navigateTo,
  onNavigate,
}: {
  msg: MessageSearchResult
  direction: string
  navigateTo: string
  onNavigate: (path: string) => void
}) {
  return (
    <button
      onClick={() => onNavigate(navigateTo)}
      className="w-full flex items-start gap-3 px-5 py-3.5 text-left hover:bg-surface-hover active:bg-surface-active transition-colors duration-150 cursor-pointer"
    >
      <span className="text-[10px] text-text-secondary bg-surface-active rounded px-1.5 py-0.5 mt-0.5 shrink-0">
        {msg.msgType}
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-xs text-primary-600 font-medium truncate">{direction}</span>
          <span className="text-[10px] text-text-secondary shrink-0">{formatTime(msg.createdAt)}</span>
        </div>
        <p className="text-sm text-text-primary mt-1 line-clamp-2 break-all">
          {formatContent(msg.content)}
        </p>
      </div>
      <svg className="w-4 h-4 text-text-secondary shrink-0 mt-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  )
}



function UserRow({
  user,
  onNavigate,
}: {
  user: UserSearchResult
  onNavigate: (path: string) => void
}) {
  const userProfiles = useChatStore((s) => s.userProfiles)
  const detail = user.email || ''
  const profile = userProfiles[user.uid]

  return (
    <button
      onClick={() => onNavigate(`/profile/${user.uid}`)}
      className="w-full flex items-center gap-3 px-5 py-3.5 text-left hover:bg-surface-hover active:bg-surface-active transition-colors duration-150 cursor-pointer"
    >
      <Avatar src={profile?.avatar} fallback={user.nickname} size="lg" />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-text-primary truncate">{user.nickname}</span>
          <span className="text-[10px] text-text-secondary bg-surface-active rounded-md px-1.5 py-0.5 shrink-0 font-mono">
            {String(user.uid)}
          </span>
        </div>
        {detail && (
          <p className="text-xs text-text-secondary truncate mt-0.5">{detail}</p>
        )}
      </div>
      <svg className="w-4 h-4 text-text-secondary shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  )
}



function GroupRow({
  group,
  onNavigate,
}: {
  group: GroupSearchResult
  onNavigate: (path: string) => void
}) {
  const [joinStatus, setJoinStatus] = useState<'loading' | 'member' | 'pending' | 'approved' | 'rejected' | 'none'>('loading')
  const [applying, setApplying] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function check() {
      try {
        const { getGroupInfoPublic, getMyJoinRequest } = await import('@/api/group')
        const info = await getGroupInfoPublic(String(group.gid))
        if (cancelled) return
        if (info.isMember) {
          setJoinStatus('member')
          return
        }
        const myReq = await getMyJoinRequest(String(group.gid))
        if (cancelled) return
        if (myReq && myReq.status === 'pending') {
          setJoinStatus('pending')
        } else {
          setJoinStatus('none')
        }
      } catch {
        if (!cancelled) setJoinStatus('none')
      }
    }
    check()
    return () => { cancelled = true }
  }, [group.gid])

  const handleApply = async () => {
    setApplying(true)
    try {
      const { applyToJoinGroup } = await import('@/api/group')
      await applyToJoinGroup(String(group.gid))
      setJoinStatus('pending')
    } catch {

    } finally {
      setApplying(false)
    }
  }

  const handleClick = () => {
    if (joinStatus === 'member') {
      onNavigate(`/chat/group/${group.gid}`)
    }
  }

  const isMember = joinStatus === 'member'

  return (
    <div
      onClick={handleClick}
      className={`w-full flex items-center gap-3 px-5 py-3.5 text-left transition-colors duration-150 ${isMember ? 'hover:bg-surface-hover active:bg-surface-active cursor-pointer' : ''}`}
    >
      <div className="w-10 h-10 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
        <span className="text-sm font-semibold text-primary-600">
          {group.name.charAt(0).toUpperCase()}
        </span>
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-text-primary truncate">{group.name}</span>
          <span className="text-[10px] text-text-secondary bg-surface-active rounded-md px-1.5 py-0.5 shrink-0 font-mono">
            {String(group.gid)}
          </span>
        </div>
      </div>
      <div className="shrink-0 flex items-center gap-1">
        {isMember && (
          <button
            onClick={(e) => { e.stopPropagation(); onNavigate(`/group/${group.gid}/profile`) }}
            className="text-text-secondary hover:text-primary-500 cursor-pointer p-0.5"
            title="Group profile"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
          </button>
        )}
        {joinStatus === 'loading' && (
          <span className="text-[10px] text-text-secondary">...</span>
        )}
        {joinStatus === 'none' && (
          <button
            onClick={(e) => { e.stopPropagation(); handleApply() }}
            disabled={applying}
            className="text-xs px-3 py-1 rounded-lg bg-primary-500 text-white hover:bg-primary-400 disabled:opacity-50 cursor-pointer transition-colors"
          >
            {applying ? '...' : 'Join'}
          </button>
        )}
        {joinStatus === 'pending' && (
          <span className="text-[10px] text-warning-500 bg-warning-50 px-2 py-0.5 rounded">Pending</span>
        )}
        {joinStatus === 'approved' && (
          <span className="text-[10px] text-green-500 bg-green-50 px-2 py-0.5 rounded">Approved</span>
        )}
        {joinStatus === 'rejected' && (
          <span className="text-[10px] text-red-500 bg-red-50 px-2 py-0.5 rounded">Rejected</span>
        )}
        {isMember && (
          <span className="text-[10px] text-text-secondary bg-surface-active px-2 py-0.5 rounded">Member</span>
        )}
      </div>
      {isMember && (
        <svg className="w-4 h-4 text-text-secondary shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      )}
    </div>
  )
}
