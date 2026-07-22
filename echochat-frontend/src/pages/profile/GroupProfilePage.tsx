import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import * as groupApi from '@/api/group'
import type { GroupJoinRequest } from '@/api/group'
import { uploadAvatar } from '@/api/file'
import Avatar from '@/components/ui/Avatar'
import Modal from '@/components/ui/Modal'
import ContextMenu, { type ContextMenuItem } from '@/components/ui/ContextMenu'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import { toast } from '@/components/ui/Toast'
import type { GroupInfo, GroupMember } from '@/types/group'

export default function GroupProfilePage() {
  const { gid } = useParams<{ gid: string }>()
  const navigate = useNavigate()
  const myUid = useAuthStore((s) => s.user?.uid)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const [group, setGroup] = useState<GroupInfo | null>(null)
  const [members, setMembers] = useState<GroupMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState<'leave' | 'dissolve' | null>(null)
  const [showInvite, setShowInvite] = useState(false)
  const [inviteCode, setInviteCode] = useState<string | null>(null)
  const [inviteExpire, setInviteExpire] = useState('')

  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editAnnouncement, setEditAnnouncement] = useState('')
  const [editSlowMode, setEditSlowMode] = useState(0)
  const [editMuteAll, setEditMuteAll] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editAvatarFile, setEditAvatarFile] = useState<File | null>(null)
  const [editAvatarPreview, setEditAvatarPreview] = useState<string | null>(null)
  const avatarInputRef = useRef<HTMLInputElement>(null)
  const [muteMinutes, setMuteMinutes] = useState(10)
  const [confirmAction, setConfirmAction] = useState<{ type: 'transfer' | 'kick'; uid: string; name: string } | null>(null)
  const [pendingRequests, setPendingRequests] = useState<GroupJoinRequest[]>([])
  const [requestLoading, setRequestLoading] = useState(false)

  const load = useCallback(async () => {
    if (!gid) return
    setLoading(true)
    try {
      const [g, m] = await Promise.all([
        groupApi.getGroupInfo(gid),
        groupApi.getGroupMembers(gid),
      ])
      setGroup(g)
      setMembers(m)
      for (const member of m) {
        if (!userProfiles[member.uid]?.nickname) {
          loadUserProfile(member.uid)
        }
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : ''
      setError(msg || 'Failed to load group')
      toast(msg || 'Failed to load group', 'error')
    } finally {
      setLoading(false)
    }
  }, [gid, loadUserProfile, userProfiles])

  useEffect(() => { load() }, [load])

  const roleOrder: Record<string, number> = { owner: 0, admin: 1, member: 2 }
  const sortedMembers = [...members].sort((a, b) =>
    (roleOrder[a.role] ?? 3) - (roleOrder[b.role] ?? 3),
  )

  const isOwner = group ? String(group.ownerUid) === String(myUid) : false
  const isAdmin = isOwner || members.some((m) => String(m.uid) === String(myUid) && m.role === 'admin')

  const loadRequests = useCallback(async () => {
    if (!gid || !isAdmin) return
    setRequestLoading(true)
    try {
      const list = await groupApi.getPendingJoinRequests(gid)
      for (const req of list) {
        if (!userProfiles[req.uid]?.nickname) loadUserProfile(req.uid)
      }
      setPendingRequests(list)
    } catch { /* silently ignore */ }
    finally { setRequestLoading(false) }
  }, [gid, isAdmin, loadUserProfile, userProfiles])

  useEffect(() => { if (group) loadRequests() }, [group, loadRequests])

  const handleApproveRequest = async (id: string) => {
    if (!gid) return
    try {
      await groupApi.approveJoinRequest(id)
      toast('Request approved', 'success')
      setPendingRequests((prev) => prev.filter((r) => r.id !== id))
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to approve', 'error')
    }
  }

  const handleRejectRequest = async (id: string) => {
    if (!gid) return
    try {
      await groupApi.rejectJoinRequest(id)
      toast('Request rejected')
      setPendingRequests((prev) => prev.filter((r) => r.id !== id))
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to reject', 'error')
    }
  }

  const handleLeave = async () => {
    if (!gid) return
    setActionLoading('leave')
    try {
      await groupApi.leaveGroup(gid)
      toast('Left the group', 'success')
      navigate('/chat', { replace: true })
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to leave', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  const handleDissolve = async () => {
    if (!gid) return
    setActionLoading('dissolve')
    try {
      await groupApi.dissolveGroup(gid)
      toast('Group dissolved', 'success')
      navigate('/chat', { replace: true })
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to dissolve', 'error')
    } finally {
      setActionLoading(null)
    }
  }

  const handleCreateInvite = async () => {
    if (!gid) return
    try {
      const invite = await groupApi.createInvite(gid, 24)
      setInviteCode(invite.code)
      setInviteExpire(invite.expireAt)
      setShowInvite(true)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to create invite', 'error')
    }
  }

  const handleCopyCode = () => {
    if (inviteCode) {
      navigator.clipboard.writeText(inviteCode).then(() => {
        toast('Invite code copied', 'success')
      }).catch((e) => { console.error('Failed to copy invite code:', e) })
    }
  }

  const handleKick = async (uid: string) => {
    if (!gid) return
    try {
      await groupApi.kickMember(gid, uid)
      toast('Member removed', 'success')
      setMembers((prev) => prev.filter((m) => m.uid !== uid))
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to kick member', 'error')
    }
  }

  const handleSetRole = async (uid: string, role: string) => {
    if (!gid) return
    try {
      await groupApi.setRole(gid, uid, role)
      toast(`Role set to ${role}`, 'success')
      setMembers((prev) =>
        prev.map((m) => (m.uid === uid ? { ...m, role: role as GroupMember['role'] } : m)),
      )
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to change role', 'error')
    }
  }

  const handleMute = async (uid: string) => {
    if (!gid) return
    try {
      await groupApi.muteMember(gid, uid, muteMinutes)
      toast(`Muted for ${muteMinutes} minutes`, 'success')
      const muteUntil = new Date(Date.now() + muteMinutes * 60 * 1000).toISOString()
      setMembers((prev) => prev.map((m) => (m.uid === uid ? { ...m, muteUntil } : m)))
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to mute', 'error')
    }
  }

  const handleUnmute = async (uid: string) => {
    if (!gid) return
    try {
      await groupApi.unmuteMember(gid, uid)
      toast('Unmuted', 'success')
      setMembers((prev) => prev.map((m) => (m.uid === uid ? { ...m, muteUntil: null } : m)))
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to unmute', 'error')
    }
  }

  const handleConfirm = () => {
    if (!confirmAction) return
    if (confirmAction.type === 'transfer') {
      handleTransfer(confirmAction.uid)
    } else {
      handleKick(confirmAction.uid)
    }
    setConfirmAction(null)
  }

  const handleTransfer = async (uid: string) => {
    if (!gid) return
    try {
      await groupApi.transferOwner(gid, uid)
      toast('Owner transferred', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to transfer', 'error')
    }
  }

  const startEdit = () => {
    if (!group) return
    setEditName(group.name)
    setEditAnnouncement(group.announcement || '')
    setEditSlowMode(group.slowModeInterval || 0)
    setEditMuteAll(false)
    setEditAvatarFile(null)
    setEditAvatarPreview(null)
    setEditing(true)
  }

  const handleSaveEdit = async () => {
    if (!gid) return
    setEditSaving(true)
    try {
      const updates: Partial<GroupInfo> = {
        name: editName,
        announcement: editAnnouncement,
        slowModeInterval: editSlowMode,
      }

      if (editAvatarFile) {
        const fileId = await uploadAvatar(editAvatarFile)
        updates.avatar = fileId
      }

      await groupApi.updateGroup(gid, updates)

      if (editMuteAll) {
        await groupApi.muteAll(gid)
        toast('All members muted', 'success')
      } else {
        try {
          await groupApi.unmuteAll(gid)
        } catch { /* group may not be muted */ }
      }

      toast('Settings saved', 'success')
      setGroup((prev) =>
        prev
          ? { ...prev, name: editName, announcement: editAnnouncement, slowModeInterval: editSlowMode, avatar: updates.avatar ?? prev.avatar }
          : prev,
      )
      setEditing(false)
      setEditAvatarFile(null)
      setEditAvatarPreview(null)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to save', 'error')
    } finally {
      setEditSaving(false)
    }
  }

  const handleAvatarPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setEditAvatarFile(file)
    setEditAvatarPreview(URL.createObjectURL(file))
  }

  const getMemberContextItems = (m: GroupMember): ContextMenuItem[] => {
    const isSelf = String(m.uid) === String(myUid)
    if (isSelf || !isAdmin) return []

    const isTargetOwner = m.role === 'owner'
    const isTargetAdmin = m.role === 'admin'
    const items: ContextMenuItem[] = []

    if (!isTargetOwner) {
      if (isOwner) {
        if (isTargetAdmin) {
          items.push({ label: 'Demote to Member', onClick: () => handleSetRole(m.uid, 'member') })
        } else {
          items.push({ label: 'Promote to Admin', onClick: () => handleSetRole(m.uid, 'admin') })
        }
      }

      if (m.muteUntil && new Date(m.muteUntil) > new Date()) {
        items.push({ label: 'Unmute', onClick: () => handleUnmute(m.uid) })
      } else {
        items.push({
          label: 'Mute',
          children: [
            { label: '5 minutes', onClick: () => { setMuteMinutes(5); setTimeout(() => handleMute(m.uid), 100) } },
            { label: '30 minutes', onClick: () => { setMuteMinutes(30); setTimeout(() => handleMute(m.uid), 100) } },
            { label: '1 hour', onClick: () => { setMuteMinutes(60); setTimeout(() => handleMute(m.uid), 100) } },
            { label: '12 hours', onClick: () => { setMuteMinutes(720); setTimeout(() => handleMute(m.uid), 100) } },
            { label: '24 hours', onClick: () => { setMuteMinutes(1440); setTimeout(() => handleMute(m.uid), 100) } },
          ],
        })
      }

      if (isOwner) {
        items.push({
          label: 'Transfer Ownership',
          danger: true,
          onClick: () => setConfirmAction({ type: 'transfer', uid: m.uid, name: userProfiles[m.uid]?.nickname || m.nickname || m.uid }),
        })
      }

      items.push({
        label: 'Remove from Group',
        danger: true,
        onClick: () => setConfirmAction({ type: 'kick', uid: m.uid, name: userProfiles[m.uid]?.nickname || m.nickname || m.uid }),
      })
    }

    return items
  }

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center animate-fade-in">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (!group) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 animate-fade-in">
        <p className="text-sm text-text-secondary">
          {error || 'Group not found'}
        </p>
        <button
          onClick={() => { setError(null); load() }}
          className="text-xs text-primary-500 hover:text-primary-400 cursor-pointer"
        >
          Retry
        </button>
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-6 py-4 bg-surface border-b border-border-light shrink-0 flex items-center justify-between">
        <h2 className="text-base font-semibold text-text-primary">Group Profile</h2>
        <button
          onClick={() => navigate(-1)}
          className="text-sm text-text-secondary hover:text-text-primary transition-colors cursor-pointer"
        >
          Back
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="flex flex-col items-center gap-3 px-6 py-8 border-b border-border-light">
          <Avatar src={group.avatar} fallback={group.name} size="xl" />
          <div className="text-center">
            <p className="text-base font-semibold text-text-primary">{group.name}</p>
            <p className="text-[11px] text-text-secondary font-mono mt-0.5">GID: {group.gid}</p>
          </div>
          {group.announcement && (
            <p className="text-sm text-text-secondary text-center max-w-md bg-surface-active rounded-lg px-4 py-2">
              {group.announcement}
            </p>
          )}
          {group.slowModeInterval > 0 && (
            <p className="text-xs text-amber-600 bg-amber-50 dark:bg-amber-950 rounded-md px-2 py-1">
              Slow mode: {group.slowModeInterval}s
            </p>
          )}
          <div className="flex items-center gap-2 mt-1">
            {isAdmin && (
              <>
                <button
                  onClick={startEdit}
                  className="px-4 py-2 rounded-lg border border-border-light text-text-primary text-sm font-medium hover:bg-surface-hover transition-colors cursor-pointer"
                >
                  Edit
                </button>
                <button
                  onClick={handleCreateInvite}
                  className="px-4 py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 transition-colors cursor-pointer"
                >
                  Invite
                </button>
              </>
            )}
            {isOwner && (
              <button
                onClick={handleDissolve}
                disabled={actionLoading !== null}
                className="px-4 py-2 rounded-lg border border-red-200 dark:border-red-800 text-red-500 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer disabled:opacity-40"
              >
                {actionLoading === 'dissolve' ? '...' : 'Dissolve'}
              </button>
            )}
            {!isOwner && (
              <button
                onClick={handleLeave}
                disabled={actionLoading !== null}
                className="px-4 py-2 rounded-lg border border-red-200 dark:border-red-800 text-red-500 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer disabled:opacity-40"
              >
                {actionLoading === 'leave' ? '...' : 'Leave Group'}
              </button>
            )}
          </div>
        </div>

        <div className="px-6 py-4">
          <h3 className="text-sm font-medium text-text-primary mb-3">
            Members ({sortedMembers.length})
          </h3>
          <div className="divide-y divide-border-light">
            {sortedMembers.map((m) => {
              const profile = userProfiles[m.uid]
              const name = profile?.nickname || m.nickname || `User ${m.uid.slice(-8)}`
              const roleLabel = m.role === 'owner' ? 'Owner' : m.role === 'admin' ? 'Admin' : 'Member'
              const contextItems = getMemberContextItems(m)
              const isMuted = m.muteUntil && new Date(m.muteUntil) > new Date()

              const row = (
                <div
                  className={`flex items-center gap-3 py-2.5 ${isMuted ? 'opacity-60' : ''} cursor-pointer hover:bg-surface-hover rounded-lg px-2 -mx-2 transition-colors`}
                  onClick={() => navigate(`/profile/${m.uid}`)}
                  title={`View ${name}'s profile`}
                >
                  <Avatar
                    src={profile?.avatar || m.avatar}
                    fallback={name}
                    size="sm"
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm text-text-primary hover:text-primary-500 truncate">{name}</span>
                      {isMuted && (
                        <svg className="w-3.5 h-3.5 text-text-secondary shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <line x1="1" y1="1" x2="23" y2="23" /><path d="M9 9v3a3 3 0 005.12 2.12M15 9.34V4a3 3 0 00-5.94-.6" /><path d="M17 16.95A7 7 0 015 12v-2m14 0v2a7 7 0 01-.11 1.23" /><line x1="12" y1="19" x2="12" y2="23" /><line x1="8" y1="23" x2="16" y2="23" />
                        </svg>
                      )}
                      {String(m.uid) === String(myUid) && (
                        <span className="text-[10px] text-text-secondary bg-surface-active rounded px-1">You</span>
                      )}
                    </div>
                  </div>
                  <span className={`text-[10px] font-medium px-2 py-0.5 rounded ${
                    roleLabel === 'Owner'
                      ? 'bg-amber-50 dark:bg-amber-950 text-amber-600'
                      : roleLabel === 'Admin'
                        ? 'bg-blue-50 dark:bg-blue-950 text-blue-600'
                        : 'bg-surface-active text-text-secondary'
                  }`}>
                    {roleLabel}
                  </span>
                </div>
              )

              if (contextItems.length > 0) {
                return (
                  <ContextMenu key={m.uid || m.id} items={contextItems}>
                    {row}
                  </ContextMenu>
                )
              }
              return <div key={m.uid || m.id}>{row}</div>
            })}
          </div>
        </div>

        {isAdmin && (
          <div className="px-6 py-4 border-t border-border-light">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-medium text-text-primary">
                Join Requests ({pendingRequests.length})
              </h3>
              <button onClick={loadRequests} className="text-[10px] text-primary-500 hover:text-primary-400 cursor-pointer">
                Refresh
              </button>
            </div>
            {requestLoading ? (
              <p className="text-xs text-text-secondary py-2">Loading...</p>
            ) : pendingRequests.length === 0 ? (
              <p className="text-xs text-text-secondary py-2">No pending requests</p>
            ) : (
              <div className="divide-y divide-border-light">
                {pendingRequests.map((req) => {
                  const profile = userProfiles[req.uid]
                  const name = profile?.nickname || `User ${req.uid.slice(-8)}`
                  return (
                    <div key={req.id} className="flex items-center gap-3 py-2.5">
                      <Avatar src={profile?.avatar} fallback={name} size="sm" />
                      <div className="flex-1 min-w-0">
                        <span className="text-sm text-text-primary">{name}</span>
                        {req.message && (
                          <p className="text-xs text-text-secondary truncate mt-0.5">{req.message}</p>
                        )}
                      </div>
                      <div className="flex gap-1.5 shrink-0">
                        <button
                          onClick={() => handleApproveRequest(req.id)}
                          className="text-xs px-2.5 py-1 rounded bg-green-500 text-white hover:bg-green-400 cursor-pointer transition-colors"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => handleRejectRequest(req.id)}
                          className="text-xs px-2.5 py-1 rounded bg-red-500 text-white hover:bg-red-400 cursor-pointer transition-colors"
                        >
                          Reject
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>

      <Modal open={editing} onClose={() => setEditing(false)} title="Edit Group" size="sm">
        <div className="flex flex-col gap-4">
          <div className="flex flex-col items-center gap-2">
            <input
              ref={avatarInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleAvatarPick}
            />
            <div
              onClick={() => avatarInputRef.current?.click()}
              className="relative cursor-pointer group"
            >
              <Avatar
                src={editAvatarPreview || group.avatar}
                fallback={group.name}
                size="xl"
              />
              <div className="absolute inset-0 rounded-full bg-black/30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z" />
                  <circle cx="12" cy="13" r="4" />
                </svg>
              </div>
            </div>
            <span className="text-xs text-text-secondary">
              {editAvatarFile ? editAvatarFile.name : 'Click to change avatar'}
            </span>
          </div>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Group Name</span>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full rounded-lg border border-border-light bg-surface-active px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-100 transition-colors"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Announcement</span>
            <textarea
              value={editAnnouncement}
              onChange={(e) => setEditAnnouncement(e.target.value)}
              rows={3}
              className="w-full rounded-lg border border-border-light bg-surface-active px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-100 transition-colors resize-none"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Slow Mode (seconds, 0 = off)</span>
            <select
              value={editSlowMode}
              onChange={(e) => setEditSlowMode(Number(e.target.value))}
              className="w-full rounded-lg border border-border-light bg-surface-active px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-100 transition-colors cursor-pointer"
            >
              <option value={0}>Off</option>
              <option value={5}>5 seconds</option>
              <option value={10}>10 seconds</option>
              <option value={30}>30 seconds</option>
              <option value={60}>1 minute</option>
              <option value={300}>5 minutes</option>
            </select>
          </label>
          <div className="flex items-center justify-between py-1">
            <span className="text-sm text-text-primary">Mute All</span>
            <button
              onClick={() => setEditMuteAll((v) => !v)}
              className={`relative w-10 h-6 rounded-full transition-colors cursor-pointer ${editMuteAll ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600'}`}
            >
              <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${editMuteAll ? 'translate-x-4' : ''}`} />
            </button>
          </div>
          <div className="flex items-center gap-2 pt-2">
            <button
              onClick={() => setEditing(false)}
              className="flex-1 py-2 rounded-lg border border-border-light text-sm text-text-secondary hover:bg-surface-hover transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              onClick={handleSaveEdit}
              disabled={editSaving || !editName.trim()}
              className="flex-1 py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
            >
              {editSaving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      </Modal>

      <Modal open={showInvite} onClose={() => { setShowInvite(false); setInviteCode(null) }} title="Invite Code" size="sm">
        {inviteCode && (
          <div className="flex flex-col gap-3">
            <div className="bg-surface-active rounded-lg p-4 text-center">
              <p className="text-2xl font-mono font-bold text-text-primary tracking-widest select-all">{inviteCode}</p>
              <p className="text-xs text-text-secondary mt-2">
                Expires: {new Date(inviteExpire).toLocaleString()}
              </p>
            </div>
            <button
              onClick={handleCopyCode}
              className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 transition-colors cursor-pointer"
            >
              Copy Code
            </button>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={confirmAction !== null}
        onClose={() => setConfirmAction(null)}
        onConfirm={handleConfirm}
        title={confirmAction?.type === 'transfer' ? 'Transfer Ownership' : 'Remove Member'}
        message={
          confirmAction?.type === 'transfer'
            ? `Transfer group ownership to ${confirmAction.name}? This cannot be undone.`
            : `Remove ${confirmAction?.name} from the group?`
        }
        confirmLabel={confirmAction?.type === 'transfer' ? 'Transfer' : 'Remove'}
        danger
      />
    </div>
  )
}
