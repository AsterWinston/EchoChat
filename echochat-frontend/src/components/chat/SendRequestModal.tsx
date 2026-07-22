import { useState, useCallback, useEffect } from 'react'
import Modal from '@/components/ui/Modal'
import Avatar from '@/components/ui/Avatar'
import { useChatStore } from '@/stores/chatStore'
import { useAuthStore } from '@/stores/authStore'
import * as searchApi from '@/api/search'
import { getFriendList, getSentRequests } from '@/api/friend'
import { toast } from '@/components/ui/Toast'
import type { UserSearchResult } from '@/types/search'

function shortId(id: string | number): string {
  const s = String(id)
  if (s.length <= 12) return s
  return s.slice(0, 6) + '\u2026' + s.slice(-6)
}

interface Props {
  open: boolean
  onClose: () => void
  onSend: (uid: string, message: string) => void
}

export default function SendRequestModal({ open, onClose, onSend }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<UserSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<UserSearchResult | null>(null)
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [friendUids, setFriendUids] = useState<Set<string>>(new Set())
  const [requestedUids, setRequestedUids] = useState<Set<string>>(new Set())
  const currentUid = useAuthStore((s) => s.user?.uid)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)

  useEffect(() => {
    if (!open) return
    Promise.all([
      getFriendList().then((friends) => setFriendUids(new Set(friends.map((f) => String(f.uid))))).catch((e) => { console.error('Failed to load friend list:', e) }),
      getSentRequests('pending').then((reqs) => setRequestedUids(new Set(reqs.map((r) => String(r.toUid))))).catch((e) => { console.error('Failed to load sent requests:', e) }),
    ])
  }, [open])

  useEffect(() => {
    for (const u of results) loadUserProfile(String(u.uid))
  }, [results, loadUserProfile])

  const handleSearch = useCallback(async (q: string) => {
    setQuery(q)
    if (!q.trim()) { setResults([]); return }
    setLoading(true)
    try { setResults(await searchApi.searchUsers(q)) } catch { setResults([]) }
    finally { setLoading(false) }
  }, [])

  const handleSend = async () => {
    if (!selected || sending || isSelectedFriend || isSelectedSelf || isRequested) return
    setSending(true)
    try {
      await onSend(String(selected.uid), message)
      toast('Friend request sent', 'success')
      setQuery('')
      setResults([])
      setSelected(null)
      setMessage('')
      onClose()
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Request failed', 'error')
    } finally {
      setSending(false)
    }
  }

  const handleClose = () => {
    setQuery(''); setResults([]); setSelected(null); setMessage(''); onClose()
  }

  const isSelectedFriend = selected ? friendUids.has(String(selected.uid)) : false
  const isSelectedSelf = selected ? String(selected.uid) === String(currentUid) : false
  const isRequested = selected ? requestedUids.has(String(selected.uid)) : false

  return (
    <Modal open={open} onClose={handleClose} title="Send Friend Request" size="sm">
      <div className="flex flex-col gap-3">
        {!selected ? (
          <>
            <div className="relative">
              <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
              </svg>
              <input type="text" value={query} onChange={(e) => handleSearch(e.target.value)} autoFocus
                placeholder="Search user\u2026"
                className="w-full pl-9 pr-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200" />
            </div>

            {loading && <div className="flex justify-center py-3"><svg className="animate-spin h-4 w-4 text-primary-500" viewBox="0 0 24 24" fill="none"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg></div>}

            {!loading && results.length === 0 && query.trim() && (
              <p className="text-xs text-text-secondary text-center py-3">No users found</p>
            )}

            {results.map((u) => {
              const uid = String(u.uid)
              const profile = userProfiles[uid]
              const name = profile?.nickname || u.nickname
              const avatar = profile?.avatar
              const isFriend = friendUids.has(uid)
              const isRequested = requestedUids.has(uid)
              const isSelf = uid === String(currentUid)
              const disabled = isFriend || isSelf || isRequested

              return (
                <button
                  key={u.uid}
                  onClick={() => { if (!disabled) { setSelected(u); setMessage('') } }}
                  disabled={disabled}
                  className="flex items-center gap-3 p-2 rounded-lg hover:bg-surface-hover transition-colors cursor-pointer text-left disabled:opacity-60 disabled:cursor-default"
                >
                  <Avatar src={avatar} fallback={name} size="sm" />
                  <div className="flex-1 min-w-0">
                    <span className="text-sm text-text-primary block truncate">{name}</span>
                    <span className="text-[10px] text-text-secondary font-mono" title={uid}>{shortId(uid)}</span>
                  </div>
                  {isSelf && (
                    <span className="text-[10px] text-text-secondary bg-surface-active rounded px-1.5 py-0.5 shrink-0">You</span>
                  )}
                  {isFriend && (
                    <span className="text-[10px] text-primary-500 bg-primary-50 rounded px-1.5 py-0.5 shrink-0 font-medium">Friend</span>
                  )}
                  {isRequested && (
                    <span className="text-[10px] text-amber-500 bg-amber-50 dark:bg-amber-950 rounded px-1.5 py-0.5 shrink-0 font-medium">Requested</span>
                  )}
                </button>
              )
            })}
          </>
        ) : (
          <>
            <div className="flex items-center gap-3 p-2 bg-surface-active rounded-lg">
              <Avatar src={userProfiles[String(selected.uid)]?.avatar} fallback={selected.nickname} size="md" />
              <div className="flex-1 min-w-0">
                <span className="text-sm font-medium text-text-primary block">{selected.nickname}</span>
                <span className="text-[10px] text-text-secondary font-mono" title={String(selected.uid)}>{shortId(selected.uid)}</span>
              </div>
              <button onClick={() => setSelected(null)} className="text-xs text-text-secondary hover:text-text-primary cursor-pointer">Change</button>
            </div>
            {isSelectedFriend ? (
              <p className="text-sm text-text-secondary text-center py-2">This user is already your friend.</p>
            ) : isRequested ? (
              <p className="text-sm text-text-secondary text-center py-2">A request is already pending for this user.</p>
            ) : isSelectedSelf ? (
              <p className="text-sm text-text-secondary text-center py-2">You cannot send a request to yourself.</p>
            ) : (
              <>
                <textarea value={message} onChange={(e) => setMessage(e.target.value)} rows={2}
                  placeholder="Add a message\u2026(optional)"
                  className="w-full resize-none bg-surface-active rounded-lg px-3 py-2 text-sm border-none outline-none focus:ring-1 focus:ring-primary-200" />
                <button onClick={handleSend} disabled={sending}
                  className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer">
                  {sending ? 'Sending...' : 'Send Request'}
                </button>
              </>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}
