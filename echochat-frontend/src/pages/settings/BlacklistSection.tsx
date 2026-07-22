import { useState, useEffect, useCallback } from 'react'
import { getBlacklist, unblockUser } from '@/api/friend'
import { useChatStore } from '@/stores/chatStore'
import Avatar from '@/components/ui/Avatar'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import { toast } from '@/components/ui/Toast'
import type { BlacklistUser } from '@/api/friend'

export default function BlacklistSection() {
  const [list, setList] = useState<BlacklistUser[]>([])
  const [loading, setLoading] = useState(true)
  const [confirmUid, setConfirmUid] = useState<string | null>(null)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getBlacklist()
      setList(data ?? [])
      for (const u of data ?? []) {
        if (!userProfiles[u.uid]?.nickname) loadUserProfile(u.uid)
      }
    } catch {
      setList([])
    } finally {
      setLoading(false)
    }
  }, [loadUserProfile, userProfiles])

  useEffect(() => { load() }, [load])

  const handleUnblock = async (uid: string) => {
    try {
      await unblockUser(uid)
      setList((prev) => prev.filter((u) => u.uid !== uid))
      toast('Unblocked', 'success')
    } catch {
      toast('Failed to unblock', 'error')
    }
    setConfirmUid(null)
  }

  return (
    <div className="bg-surface rounded-xl border border-border-light p-5">
      <h3 className="text-sm font-medium text-text-primary mb-4">Blocked Users</h3>
      {loading ? (
        <div className="py-8 flex items-center justify-center">
          <svg className="animate-spin h-5 w-5 text-primary-500" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
      ) : list.length === 0 ? (
        <p className="text-sm text-text-secondary py-4">No blocked users</p>
      ) : (
        <div className="divide-y divide-border-light">
          {list.map((u) => {
            const name = userProfiles[u.uid]?.nickname || u.nickname || u.uid
            return (
              <div key={u.uid} className="flex items-center gap-3 py-2.5">
                <Avatar src={userProfiles[u.uid]?.avatar || u.avatar} fallback={name} size="sm" />
                <span className="flex-1 text-sm text-text-primary truncate">{name}</span>
                <button
                  onClick={() => setConfirmUid(u.uid)}
                  className="text-xs text-text-secondary hover:text-red-500 px-2 py-1 rounded transition-colors cursor-pointer"
                >
                  Unblock
                </button>
              </div>
            )
          })}
        </div>
      )}

      <ConfirmDialog
        open={confirmUid !== null}
        onClose={() => setConfirmUid(null)}
        onConfirm={() => confirmUid && handleUnblock(confirmUid)}
        title="Unblock User"
        message="This user will be able to send you messages and see your moments."
        confirmLabel="Unblock"
      />
    </div>
  )
}
