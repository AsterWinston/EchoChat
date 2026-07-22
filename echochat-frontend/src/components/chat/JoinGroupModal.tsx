import { useState, useCallback, type FormEvent } from 'react'
import Modal from '@/components/ui/Modal'
import * as groupApi from '@/api/group'
import type { InviteInfo } from '@/api/group'

interface Props {
  open: boolean
  onClose: () => void
  onJoined: () => void
}

export default function JoinGroupModal({ open, onClose, onJoined }: Props) {
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [joining, setJoining] = useState(false)
  const [error, setError] = useState('')
  const [inviteInfo, setInviteInfo] = useState<InviteInfo | null>(null)

  const handleClose = useCallback(() => {
    setCode('')
    setError('')
    setInviteInfo(null)
    onClose()
  }, [onClose])

  const handleLookup = async (e: FormEvent) => {
    e.preventDefault()
    const trimmed = code.trim()
    if (!trimmed) return
    setLoading(true)
    setError('')
    setInviteInfo(null)
    try {
      const info = await groupApi.getInviteInfo(trimmed)
      setInviteInfo(info)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid invite code')
    } finally {
      setLoading(false)
    }
  }

  const handleJoin = async () => {
    if (!inviteInfo || joining) return
    setJoining(true)
    setError('')
    try {
      await groupApi.joinByInvite(inviteInfo.code)
      onJoined()
      handleClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to join group')
    } finally {
      setJoining(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Join Group" size="sm">
      <div className="flex flex-col gap-3">
        {!inviteInfo ? (
          <form onSubmit={handleLookup} className="flex flex-col gap-3">
            <input
              type="text"
              value={code}
              onChange={(e) => { setCode(e.target.value); setError('') }}
              placeholder="Enter invite code"
              autoFocus
              className="w-full px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200 font-mono"
            />
            {error && <p className="text-xs text-red-500">{error}</p>}
            <button
              type="submit"
              disabled={loading || !code.trim()}
              className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
            >
              {loading ? 'Looking up...' : 'Look up'}
            </button>
          </form>
        ) : (
          <div className="flex flex-col gap-3">
            <div className="bg-surface-active rounded-lg p-3">
              <p className="text-sm font-medium text-text-primary">{inviteInfo.groupName}</p>
              {inviteInfo.expired ? (
                <p className="text-xs text-red-500 mt-1">This invite has expired</p>
              ) : (
                <p className="text-xs text-text-secondary mt-1">
                  Invite expires {new Date(inviteInfo.expireAt).toLocaleString()}
                </p>
              )}
            </div>
            {error && <p className="text-xs text-red-500">{error}</p>}
            <button
              onClick={handleJoin}
              disabled={joining || inviteInfo.expired}
              className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
            >
              {joining ? 'Joining...' : 'Join Group'}
            </button>
          </div>
        )}
      </div>
    </Modal>
  )
}
