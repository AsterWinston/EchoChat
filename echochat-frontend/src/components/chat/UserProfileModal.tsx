import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import Modal from '@/components/ui/Modal'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import Avatar from '@/components/ui/Avatar'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { getUserProfile } from '@/api/user'
import { getFriendList, sendFriendRequest, deleteFriend, blockUser, unblockUser, getBlacklist } from '@/api/friend'
import { toast } from '@/components/ui/Toast'
import type { User } from '@/types/user'

interface Props {
  uid: string
  open: boolean
  onClose: () => void
}

export default function UserProfileModal({ uid, open, onClose }: Props) {
  const [profile, setProfile] = useState<User | null>(null)
  const [loading, setLoading] = useState(false)
  const [isFriend, setIsFriend] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [actionLoading, setActionLoading] = useState<'add' | 'delete' | 'block' | 'unblock' | null>(null)
  const myUid = useAuthStore((s) => s.user?.uid)
  const navigate = useNavigate()

  const isSelf = String(myUid) === uid

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [p, friends, blacklist] = await Promise.all([
        getUserProfile(uid),
        getFriendList().catch(() => []),
        getBlacklist().catch(() => []),
      ])
      setProfile(p)
      setIsFriend(friends.some((f) => String(f.uid) === uid))
      setIsBlocked(blacklist.some((b) => String(b.uid) === uid))
    } catch {
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }, [uid])

  useEffect(() => {
    if (open) load()
  }, [open, load])

  const handleSendMessage = () => {
    navigate(`/chat/${uid}`)
    onClose()
  }

  const handleAddFriend = async () => {
    setActionLoading('add')
    try {
      await sendFriendRequest(uid)
      toast('Friend request sent', 'success')
      setIsFriend(true)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  const handleDeleteFriend = async () => {
    setActionLoading('delete')
    try {
      await deleteFriend(uid)
      toast('Friend removed', 'success')
      setIsFriend(false)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  const handleBlock = async () => {
    setActionLoading('block')
    try {
      await blockUser(uid)
      toast('User blocked', 'success')
      setIsBlocked(true)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  const handleUnblock = async () => {
    setActionLoading('unblock')
    try {
      await unblockUser(uid)
      toast('User unblocked', 'success')
      setIsBlocked(false)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  return (
    <>
    <Modal open={open} onClose={onClose} title="User Profile" size="sm">
      {loading ? (
        <div className="flex justify-center py-8">
          <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
      ) : profile ? (
        <div className="flex flex-col items-center gap-4">
          <div className="flex flex-col items-center gap-2">
            <Avatar
              src={profile.avatar}
              fallback={(() => { const p = useChatStore.getState().userProfiles[uid]; return p?.memo || profile.nickname })()}
              size="xl"
              status={profile.status === 1 ? 'online' : 'offline'}
            />
            <div className="text-center">
              <p className="text-base font-semibold text-text-primary">{(() => { const p = useChatStore.getState().userProfiles[uid]; return p?.memo || profile.nickname })()}</p>
              <p className="text-[11px] text-text-secondary font-mono">UID: {String(profile.uid)}</p>
              {profile.signature && (
                <p className="text-xs text-text-secondary mt-1">{profile.signature}</p>
              )}
            </div>
          </div>

          <div className="w-full bg-surface-active rounded-lg p-3 space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-text-secondary">Gender</span>
              <span className="text-text-primary">
                {profile.gender === 1 ? 'Male' : profile.gender === 2 ? 'Female' : 'Unknown'}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-secondary">Age</span>
              <span className="text-text-primary">{profile.age || '—'}</span>
            </div>
            {profile.email && (
              <div className="flex justify-between">
                <span className="text-text-secondary">Email</span>
                <span className="text-text-primary font-mono text-[11px]">{profile.email}</span>
              </div>
            )}
          </div>

          <button
            onClick={() => { navigate(`/moments/user/${uid}`); onClose() }}
            className="w-full py-2 rounded-lg border border-border-light text-text-primary text-sm font-medium hover:bg-surface-hover transition-colors cursor-pointer"
          >
            View Moments
          </button>

          {!isSelf && (
            <div className="w-full flex flex-col gap-2">
              <button
                onClick={handleSendMessage}
                className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 transition-colors cursor-pointer"
              >
                Send Message
              </button>
              {isFriend ? (
                <button
                  onClick={() => setShowDeleteConfirm(true)}
                  disabled={actionLoading === 'delete'}
                  className="w-full py-2 rounded-lg border border-red-200 dark:border-red-800 text-red-500 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer disabled:opacity-40"
                >
                  {actionLoading === 'delete' ? 'Removing...' : 'Remove Friend'}
                </button>
              ) : (
                <button
                  onClick={handleAddFriend}
                  disabled={actionLoading === 'add'}
                  className="w-full py-2 rounded-lg border border-primary-200 text-primary-500 text-sm font-medium hover:bg-primary-50 transition-colors cursor-pointer disabled:opacity-40"
                >
                  {actionLoading === 'add' ? 'Sending...' : 'Add Friend'}
                </button>
              )}
              {isBlocked ? (
                <button
                  onClick={handleUnblock}
                  disabled={actionLoading === 'unblock'}
                  className="w-full py-2 rounded-lg text-text-secondary text-xs font-medium hover:text-text-primary hover:bg-surface-hover transition-colors cursor-pointer disabled:opacity-40"
                >
                  {actionLoading === 'unblock' ? '...' : 'Unblock'}
                </button>
              ) : (
                <button
                  onClick={handleBlock}
                  disabled={actionLoading === 'block'}
                  className="w-full py-2 rounded-lg text-text-secondary text-xs font-medium hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer disabled:opacity-40"
                >
                  {actionLoading === 'block' ? 'Blocking...' : 'Block'}
                </button>
              )}
            </div>
          )}
        </div>
      ) : (
        <p className="text-sm text-text-secondary text-center py-8">Failed to load user profile</p>
      )}
    </Modal>

    <ConfirmDialog
      open={showDeleteConfirm}
      onClose={() => setShowDeleteConfirm(false)}
      onConfirm={() => { handleDeleteFriend(); setShowDeleteConfirm(false) }}
      title="Remove Friend"
      message="Are you sure you want to remove this friend? You can add them again later."
      confirmLabel="Remove"
      danger
    />
    </>
  )
}
