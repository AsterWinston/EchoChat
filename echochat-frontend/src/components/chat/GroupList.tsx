import { useState, useEffect, useCallback } from 'react'
import { getJoinedGroups, getOwnedGroups, createGroup } from '@/api/group'
import { useChatStore } from '@/stores/chatStore'
import { listCache } from '@/stores/listCache'
import { useSearchQuery } from '@/components/chat/ChatSidebar'
import Avatar from '@/components/ui/Avatar'
import type { GroupInfo } from '@/types/group'
import CreateGroupModal from '@/components/chat/CreateGroupModal'
import JoinGroupModal from '@/components/chat/JoinGroupModal'

export default function GroupList({ onSelect }: { onSelect?: (gid: string) => void }) {
  const [ownedGroups, setOwnedGroups] = useState<GroupInfo[]>(listCache.ownedGroups ?? [])
  const [joinedGroups, setJoinedGroups] = useState<GroupInfo[]>(listCache.joinedGroups ?? [])
  const [loading, setLoading] = useState(listCache.ownedGroups === null)
  const [error, setError] = useState('')
  const [fetchTrigger, setFetchTrigger] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [showJoin, setShowJoin] = useState(false)
  const activeConversation = useChatStore((s) => s.activeConversation)
  const searchQuery = useSearchQuery()

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [owned, joined] = await Promise.all([
        getOwnedGroups().catch(() => [] as GroupInfo[]),
        getJoinedGroups().catch(() => [] as GroupInfo[]),
      ])
      listCache.ownedGroups = owned
      listCache.joinedGroups = joined.filter((g) => !owned.some((o) => o.gid === g.gid))
      setOwnedGroups(listCache.ownedGroups)
      setJoinedGroups(listCache.joinedGroups)
    } catch {
      setError('Failed to load groups')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [fetchTrigger])

  const retry = useCallback(() => {
    listCache.ownedGroups = null
    listCache.joinedGroups = null
    setFetchTrigger((t) => t + 1)
  }, [])

  const handleCreate = useCallback(async (name: string) => {
    await createGroup(name)
    listCache.ownedGroups = null
    listCache.joinedGroups = null
    setFetchTrigger((t) => t + 1)
    setShowCreate(false)
  }, [])

  const handleJoined = useCallback(() => {
    listCache.ownedGroups = null
    listCache.joinedGroups = null
    setFetchTrigger((t) => t + 1)
  }, [])

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center py-12">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center py-12">
        <div className="text-center">
          <p className="text-sm text-red-500 mb-2">{error}</p>
          <button onClick={retry} className="text-xs text-primary-500 hover:text-primary-400 cursor-pointer">
            Retry
          </button>
        </div>
      </div>
    )
  }

  const total = ownedGroups.length + joinedGroups.length

  return (
    <>
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-4 pt-4 pb-3 shrink-0">
          <span className="text-[13px] font-medium text-text-primary">Groups</span>
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setShowJoin(true)}
              className="flex items-center gap-1 text-xs font-medium text-primary-500 hover:text-primary-400 bg-primary-50 hover:bg-primary-100 rounded-lg px-2.5 py-1.5 transition-colors cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" /><polyline points="10 17 15 12 10 7" /><line x1="15" y1="12" x2="3" y2="12" />
              </svg>
              Join
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-1 text-xs font-medium text-primary-500 hover:text-primary-400 bg-primary-50 hover:bg-primary-100 rounded-lg px-2.5 py-1.5 transition-colors cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              Create
            </button>
          </div>
        </div>

        {total === 0 ? (
          <div className="flex-1 flex items-center justify-center py-12">
            <div className="text-center text-text-secondary">
              <svg className="w-12 h-12 mx-auto mb-3 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
              </svg>
              <p className="text-sm text-text-secondary">No groups yet</p>
              <p className="text-xs mt-1">Create or join a group to get started</p>
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto">
            {ownedGroups.length > 0 && (
              <>
                <div className="px-4 pt-3 pb-1.5 text-[11px] font-medium text-text-secondary uppercase tracking-wide">
                  My Groups
                </div>
                {ownedGroups.filter((g) => !searchQuery || g.name.toLowerCase().includes(searchQuery)).map((group) => (
                  <GroupRow key={group.gid} group={group} isOwner isActive={activeConversation?.targetId === group.gid} onSelect={onSelect} />
                ))}
              </>
            )}
            {joinedGroups.length > 0 && (
              <>
                <div className="px-4 pt-3 pb-1.5 text-[11px] font-medium text-text-secondary uppercase tracking-wide">
                  Joined
                </div>
                {joinedGroups.filter((g) => !searchQuery || g.name.toLowerCase().includes(searchQuery)).map((group) => (
                  <GroupRow key={group.gid} group={group} isActive={activeConversation?.targetId === group.gid} onSelect={onSelect} />
                ))}
              </>
            )}
          </div>
        )}
      </div>

      <CreateGroupModal open={showCreate} onClose={() => setShowCreate(false)} onCreate={handleCreate} />
      <JoinGroupModal open={showJoin} onClose={() => setShowJoin(false)} onJoined={handleJoined} />
    </>
  )
}



function GroupRow({
  group,
  isOwner,
  isActive,
  onSelect,
}: {
  group: GroupInfo
  isOwner?: boolean
  isActive?: boolean
  onSelect?: (gid: string) => void
}) {
  return (
    <button
      onClick={() => onSelect?.(group.gid)}
      className={`w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-hover active:bg-surface-active transition-colors duration-150 cursor-pointer text-left ${
        isActive ? 'bg-primary-50 dark:bg-primary-900/20 border-r-2 border-primary-500' : ''
      }`}
    >
      <div className="w-11 h-11 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
        <Avatar src={group.avatar} fallback={group.name} size="lg" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-text-primary truncate">{group.name}</span>
          {isOwner && (
            <span className="text-[10px] text-amber-600 bg-amber-50 dark:bg-amber-950 rounded-md px-1.5 py-0.5 shrink-0 font-medium">Owner</span>
          )}
        </div>
        {group.announcement ? (
          <p className="text-xs text-text-secondary truncate mt-0.5">{group.announcement}</p>
        ) : (
          <p className="text-xs text-text-secondary/50 mt-0.5">No announcement</p>
        )}
      </div>
    </button>
  )
}
