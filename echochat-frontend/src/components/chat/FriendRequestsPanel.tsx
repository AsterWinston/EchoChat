import { useState, useEffect, useCallback } from 'react'
import { useChatStore } from '@/stores/chatStore'
import { useFriendRequestStore } from '@/stores/friendRequestStore'
import * as friendApi from '@/api/friend'
import Modal from '@/components/ui/Modal'
import Avatar from '@/components/ui/Avatar'
import { toast } from '@/components/ui/Toast'
import { formatTime } from '@/utils/time'
import type { FriendRequest } from '@/types/friend'

function shortId(id: string | number): string {
  const s = String(id)
  if (s.length <= 12) return s
  return s.slice(0, 6) + '\u2026' + s.slice(-6)
}

interface Props {
  open: boolean
  onClose: () => void
}



export default function FriendRequestsModal({ open, onClose }: Props) {
  const [received, setReceived] = useState<FriendRequest[]>([])
  const [sent, setSent] = useState<FriendRequest[]>([])
  const [tab, setTab] = useState<'received' | 'sent'>('received')
  const [loading, setLoading] = useState(false)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const refreshFriendCount = useFriendRequestStore((s) => s.refresh)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [r, s] = await Promise.all([
        friendApi.getReceivedRequests('pending'),
        friendApi.getSentRequests('pending'),
      ])
      setReceived(r)
      setSent(s)
      const uids = new Set<string>()
      for (const req of r) uids.add(String(req.fromUid))
      for (const req of s) uids.add(String(req.toUid))
      for (const uid of uids) loadUserProfile(uid)
    } catch {  }
    finally { setLoading(false) }
  }, [loadUserProfile])

  useEffect(() => { if (open) load() }, [open, load])

  const handleAccept = async (id: string) => {
    try { await friendApi.acceptFriendRequest(id); load(); refreshFriendCount(); toast('Friend request accepted', 'success') } catch {}
  }

  const handleReject = async (id: string) => {
    try { await friendApi.rejectFriendRequest(id); load(); refreshFriendCount(); toast('Friend request rejected', 'warning') } catch {}
  }

  const pendingReceived = received.filter((r) => r.status === 'pending')
  const pendingSent = sent.filter((s) => s.status === 'pending')

  return (
      <Modal open={open} onClose={onClose} title="Friend Requests" size="sm">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center border-t border-border-light w-full">
            <button onClick={() => setTab('received')}
              className={`flex-1 py-2 text-xs font-medium transition-colors cursor-pointer ${tab === 'received' ? 'text-primary-600 border-b-2 border-primary-500' : 'text-text-secondary hover:text-text-primary border-b-2 border-transparent'}`}>
              Received {pendingReceived.length > 0 && `(${pendingReceived.length})`}
            </button>
            <button onClick={() => setTab('sent')}
              className={`flex-1 py-2 text-xs font-medium transition-colors cursor-pointer ${tab === 'sent' ? 'text-primary-600 border-b-2 border-primary-500' : 'text-text-secondary hover:text-text-primary border-b-2 border-transparent'}`}>
              Sent {pendingSent.length > 0 && `(${pendingSent.length})`}
            </button>
          </div>
        </div>

        <div className="divide-y divide-border-light max-h-72 overflow-y-auto">
          {loading && (
            <div className="flex justify-center py-4"><svg className="animate-spin h-4 w-4 text-primary-500" viewBox="0 0 24 24" fill="none"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg></div>
          )}
          {!loading && tab === 'received' && pendingReceived.length === 0 && (
            <p className="text-xs text-text-secondary text-center py-4">No pending requests</p>
          )}
          {!loading && tab === 'received' && pendingReceived.map((r) => {
            const profile = userProfiles[String(r.fromUid)]
            const name = profile?.nickname || shortId(r.fromUid)
            return (
              <div key={r.id} className="flex items-center gap-2 px-1 py-2.5">
                <Avatar src={profile?.avatar} fallback={name} size="sm" />
                <div className="flex-1 min-w-0">
                  <span className="text-xs font-medium text-text-primary block truncate">{name}</span>
                  {r.message && <span className="text-[10px] text-text-secondary truncate block">{r.message}</span>}
                  <span className="text-[10px] text-text-secondary/50">{formatTime(r.createdAt)}</span>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <button onClick={() => handleAccept(String(r.id))} className="text-[10px] font-medium text-primary-500 hover:text-primary-400 bg-primary-50 hover:bg-primary-100 rounded px-2 py-1 transition-colors cursor-pointer">Accept</button>
                  <button onClick={() => handleReject(String(r.id))} className="text-[10px] text-text-secondary hover:text-red-500 rounded px-2 py-1 hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer">Reject</button>
                </div>
              </div>
            )
          })}
          {!loading && tab === 'sent' && pendingSent.length === 0 && (
            <p className="text-xs text-text-secondary text-center py-4">No pending sent requests</p>
          )}
          {!loading && tab === 'sent' && pendingSent.map((s) => {
            const profile = userProfiles[String(s.toUid)]
            const name = profile?.nickname || shortId(s.toUid)
            return (
              <div key={s.id} className="flex items-center gap-2 px-1 py-2.5">
                <Avatar src={profile?.avatar} fallback={name} size="sm" />
                <div className="flex-1 min-w-0">
                  <span className="text-xs font-medium text-text-primary block truncate">{name}</span>
                  {s.message && <span className="text-[10px] text-text-secondary truncate block">{s.message}</span>}
                  <span className="text-[10px] text-text-secondary/50">{formatTime(s.createdAt)}</span>
                </div>
                <span className="text-[10px] text-amber-500 bg-amber-50 dark:bg-amber-950 rounded px-1.5 py-0.5 shrink-0">Pending</span>
              </div>
            )
          })}
        </div>
      </Modal>
  )
}
