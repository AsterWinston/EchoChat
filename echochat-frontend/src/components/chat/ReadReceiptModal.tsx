import { useState, useEffect } from 'react'
import Modal from '@/components/ui/Modal'
import Avatar from '@/components/ui/Avatar'
import { useChatStore } from '@/stores/chatStore'
import { useAuthStore } from '@/stores/authStore'
import * as messageApi from '@/api/message'
import { getGroupMembers } from '@/api/group'
import type { GroupMember } from '@/types/group'

interface Props {
  open: boolean
  msgId: string
  gid: string
  onClose: () => void
}

export default function ReadReceiptModal({ open, msgId, gid, onClose }: Props) {
  const [readUids, setReadUids] = useState<Set<string>>(new Set())
  const [members, setMembers] = useState<GroupMember[]>([])
  const [loading, setLoading] = useState(false)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const myUid = useAuthStore((s) => s.user?.uid)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    Promise.all([
      messageApi.getReadReceipt(msgId).catch(() => ({ readCount: 0, readUids: [] as string[] })),
      getGroupMembers(gid).catch(() => [] as GroupMember[]),
    ]).then(([receipt, members]) => {
      setReadUids(new Set((receipt.readUids || []).map(String)))
      setMembers(members)
      for (const m of members) {
        if (!userProfiles[m.uid]?.nickname) loadUserProfile(m.uid)
      }
    }).finally(() => setLoading(false))
  }, [open, msgId, gid, loadUserProfile, userProfiles])

  const otherMembers = members.filter((m) => String(m.uid) !== String(myUid))
  const readMembers = otherMembers.filter((m) => readUids.has(String(m.uid)))
  const unreadMembers = otherMembers.filter((m) => !readUids.has(String(m.uid)))

  return (
    <Modal open={open} onClose={onClose} title={`Read Receipt (${readMembers.length}/${otherMembers.length})`} size="sm">
      {loading ? (
        <div className="flex justify-center py-6">
          <svg className="animate-spin h-5 w-5 text-primary-500" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
      ) : (
        <div className="divide-y divide-border-light max-h-72 overflow-y-auto">
          {readMembers.length > 0 && (
            <>
              <p className="text-xs font-medium text-emerald-500 px-1 py-1.5">Read ({readMembers.length})</p>
              {readMembers.map((m) => {
                const name = userProfiles[m.uid]?.nickname || m.nickname || m.uid
                return (
                  <div key={m.uid} className="flex items-center gap-2 px-1 py-2">
                    <Avatar src={userProfiles[m.uid]?.avatar || m.avatar} fallback={name} size="xs" />
                    <span className="text-sm text-text-primary">{name}</span>
                  </div>
                )
              })}
            </>
          )}
          {unreadMembers.length > 0 && (
            <>
              <p className="text-xs font-medium text-text-secondary px-1 py-1.5 pt-2">Unread ({unreadMembers.length})</p>
              {unreadMembers.map((m) => {
                const name = userProfiles[m.uid]?.nickname || m.nickname || m.uid
                return (
                  <div key={m.uid} className="flex items-center gap-2 px-1 py-2 opacity-50">
                    <Avatar src={userProfiles[m.uid]?.avatar || m.avatar} fallback={name} size="xs" />
                    <span className="text-sm text-text-primary">{name}</span>
                  </div>
                )
              })}
            </>
          )}
        </div>
      )}
    </Modal>
  )
}
