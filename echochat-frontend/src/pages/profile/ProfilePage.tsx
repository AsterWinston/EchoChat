import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { getUserProfile } from '@/api/user'
import { getFriendList, sendFriendRequest, getSentRequests, deleteFriend, blockUser, unblockUser, getBlacklist, updateFriend } from '@/api/friend'
import { useChatStore } from '@/stores/chatStore'
import { toast } from '@/components/ui/Toast'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import Avatar from '@/components/ui/Avatar'
import Modal from '@/components/ui/Modal'
import type { User } from '@/types/user'



export default function ProfilePage() {
  const { uid } = useParams<{ uid: string }>()
  const navigate = useNavigate()
  const ownUser = useAuthStore((s) => s.user)
  const [profile, setProfile] = useState<User | null>(null)
  const [loading, setLoading] = useState(false)
  const [isFriend, setIsFriend] = useState(false)
  const [hasPendingRequest, setHasPendingRequest] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showBlockConfirm, setShowBlockConfirm] = useState(false)
  const [showEditModal, setShowEditModal] = useState(false)
  const [editMemo, setEditMemo] = useState('')
  const [editGroupName, setEditGroupName] = useState('')
  const [editSaving, setEditSaving] = useState(false)

  const isOwn = !uid || uid === String(ownUser?.uid)

  const loadProfile = useCallback(async () => {
    if (isOwn && ownUser) {
      setProfile(ownUser)
      return
    }
    if (!uid) return
    setLoading(true)
    try {
      const [user, friends, sent, blacklist] = await Promise.all([
        getUserProfile(uid),
        getFriendList().catch(() => []),
        getSentRequests('pending').catch(() => []),
        getBlacklist().catch(() => []),
      ])
      setProfile(user)
      const friendInfo = friends.find((f) => String(f.uid) === uid)
      setIsFriend(!!friendInfo)
      setHasPendingRequest(sent.some((r) => String(r.toUid) === uid))
      setIsBlocked(blacklist.some((b) => String(b.uid) === uid))
      setEditMemo(friendInfo?.memo || '')
      setEditGroupName(friendInfo?.groupName || '')
    } catch {
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }, [uid, isOwn, ownUser])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  const handleAddFriend = async () => {
    if (!profile) return
    setActionLoading(true)
    try {
      await sendFriendRequest(String(profile.uid))
      setHasPendingRequest(true)
      toast('Friend request sent', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(false)
    }
  }

  const handleDeleteFriend = async () => {
    if (!profile) return
    setActionLoading(true)
    try {
      await deleteFriend(String(profile.uid))
      setIsFriend(false)
      toast('Friend removed', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(false)
      setShowDeleteConfirm(false)
    }
  }

  const handleBlock = async () => {
    if (!profile) return
    setActionLoading(true)
    try {
      await blockUser(String(profile.uid))
      setIsBlocked(true)
      toast('User blocked', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(false)
      setShowBlockConfirm(false)
    }
  }

  const handleUnblock = async () => {
    if (!profile) return
    setActionLoading(true)
    try {
      await unblockUser(String(profile.uid))
      setIsBlocked(false)
      toast('User unblocked', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setActionLoading(false)
    }
  }

  const openEditModal = () => {
    if (!uid) return
    const p = useChatStore.getState().userProfiles[uid]
    setEditMemo(p?.memo || '')
    setEditGroupName(p?.groupName || '')
    setShowEditModal(true)
  }

  const handleUpdateFriend = async () => {
    if (!uid) return
    setEditSaving(true)
    try {
      await updateFriend(uid, { groupName: editGroupName, memo: editMemo })
      useChatStore.getState().updateUserProfiles({ [uid]: { nickname: profile!.nickname, avatar: profile!.avatar, memo: editMemo } })
      toast('Saved', 'success')
      setShowEditModal(false)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed', 'error')
    } finally {
      setEditSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-sm text-text-secondary">User not found</p>
      </div>
    )
  }

  const genderLabel = profile.gender === 1 ? 'Male' : profile.gender === 2 ? 'Female' : 'Unknown'
  const memoName = !isOwn ? useChatStore.getState().userProfiles[profile.uid]?.memo : undefined
  const displayName = memoName || profile.nickname

  return (
    <>
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-6 py-4 bg-surface border-b border-border-light shrink-0 flex items-center gap-3">
        {!isOwn && (
          <button onClick={() => navigate(-1)} className="text-text-secondary hover:text-text-primary cursor-pointer">
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
        )}
        <h2 className="text-base font-semibold text-text-primary">{isOwn ? 'Profile' : displayName}</h2>
      </div>
      <div className="flex-1 overflow-y-auto px-6 py-6">
        <div className="bg-surface rounded-xl border border-border-light overflow-hidden">
          <div className="flex items-center gap-3 px-5 py-4 border-b border-border-light bg-bg-primary/50">
            <Avatar src={profile.avatar} fallback={displayName} size="lg" />
            <p className="text-sm font-medium text-text-primary">{displayName}</p>
          </div>
          <InfoRow label="UID" value={String(profile.uid)} />
          <InfoRow label="Nickname" value={profile.nickname} />
          {memoName && <InfoRow label="Remark" value={memoName} />}
          {profile.email && <InfoRow label="Email" value={profile.email} />}
          {profile.signature && <InfoRow label="Signature" value={profile.signature} />}
          <InfoRow label="Gender" value={genderLabel} />
          <InfoRow label="Age" value={String(profile.age)} />
        </div>

        {!isOwn && (
          <div className="mt-4 flex flex-col gap-2">
            {isFriend && (
              <button
                onClick={openEditModal}
                className="w-full h-11 border border-primary-200 text-primary-500 rounded-xl text-sm font-medium hover:bg-primary-50 transition-colors cursor-pointer"
              >
                Set Remark & Group
              </button>
            )}

            <button
              onClick={() => navigate(`/moments/user/${profile.uid}`)}
              className="w-full h-11 border border-border-light text-text-primary rounded-xl text-sm font-medium hover:bg-surface-hover cursor-pointer transition-colors"
            >
              View Moments
            </button>

            {isFriend ? (
              <>
                <button
                  onClick={() => navigate(`/chat/${profile.uid}`)}
                  className="w-full h-11 bg-primary-500 text-white rounded-xl text-sm font-medium hover:bg-primary-400 cursor-pointer transition-colors"
                >
                  Send Message
                </button>
                <button
                  onClick={() => setShowDeleteConfirm(true)}
                  disabled={actionLoading}
                  className="w-full h-11 border border-red-200 dark:border-red-800 text-red-500 rounded-xl text-sm font-medium hover:bg-red-50 dark:hover:bg-red-950 disabled:opacity-40 cursor-pointer transition-colors"
                >
                  Remove Friend
                </button>
              </>
            ) : hasPendingRequest ? (
              <button
                disabled
                className="w-full h-11 bg-surface-active text-text-secondary rounded-xl text-sm font-medium cursor-not-allowed"
              >
                Request Sent
              </button>
            ) : (
              <button
                onClick={handleAddFriend}
                disabled={actionLoading}
                className="w-full h-11 bg-primary-500 text-white rounded-xl text-sm font-medium hover:bg-primary-400 disabled:opacity-50 cursor-pointer transition-colors"
              >
                {actionLoading ? 'Sending...' : 'Add Friend'}
              </button>
            )}

            {isBlocked ? (
              <button
                onClick={handleUnblock}
                disabled={actionLoading}
                className="w-full h-11 text-text-secondary rounded-xl text-sm font-medium hover:text-text-primary hover:bg-surface-hover disabled:opacity-40 cursor-pointer transition-colors"
              >
                Unblock
              </button>
            ) : (
              <button
                onClick={() => setShowBlockConfirm(true)}
                disabled={actionLoading}
                className="w-full h-11 text-text-secondary rounded-xl text-sm font-medium hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950 disabled:opacity-40 cursor-pointer transition-colors"
              >
                Block
              </button>
            )}
          </div>
        )}
      </div>
    </div>

    <ConfirmDialog
      open={showDeleteConfirm}
      onClose={() => setShowDeleteConfirm(false)}
      onConfirm={handleDeleteFriend}
      title="Remove Friend"
      message={`Are you sure you want to remove ${displayName} from your friends?`}
      confirmLabel="Remove"
      danger
    />

    <ConfirmDialog
      open={showBlockConfirm}
      onClose={() => setShowBlockConfirm(false)}
      onConfirm={handleBlock}
      title="Block User"
      message={`Block ${displayName}? You will no longer receive messages from them.`}
      confirmLabel="Block"
      danger
    />

    <Modal open={showEditModal} onClose={() => setShowEditModal(false)} title="Set Remark & Group" size="sm">
      {profile && (
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3">
            <Avatar src={profile.avatar} fallback={displayName} size="lg" />
            <div>
              <p className="text-sm font-medium text-text-primary">{profile.nickname}</p>
              <p className="text-xs text-text-secondary font-mono">UID: {profile.uid}</p>
            </div>
          </div>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Remark / Alias</span>
            <input
              value={editMemo}
              onChange={(e) => setEditMemo(e.target.value)}
              placeholder="e.g. 老王, Lisa"
              className="w-full rounded-lg border border-border-light bg-surface-active px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-100 transition-colors"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Group / Category</span>
            <input
              value={editGroupName}
              onChange={(e) => setEditGroupName(e.target.value)}
              placeholder="e.g. Family, Work, Friends"
              className="w-full rounded-lg border border-border-light bg-surface-active px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-100 transition-colors"
            />
          </label>
          <div className="flex items-center gap-2 pt-2">
            <button
              onClick={() => setShowEditModal(false)}
              className="flex-1 py-2 rounded-lg border border-border-light text-sm text-text-secondary hover:bg-surface-hover transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              onClick={handleUpdateFriend}
              disabled={editSaving}
              className="flex-1 py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
            >
              {editSaving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      )}
    </Modal>
    </>
  )
}



function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 py-2.5 px-5 border-b border-border-light last:border-b-0">
      <span className="w-20 shrink-0 text-xs font-medium text-text-secondary">{label}</span>
      <span className="text-sm text-text-primary font-mono">{value}</span>
    </div>
  )
}
