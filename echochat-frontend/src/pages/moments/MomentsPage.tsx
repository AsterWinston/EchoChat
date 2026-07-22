import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChatStore } from '@/stores/chatStore'
import * as momentApi from '@/api/moment'
import { getFriendList } from '@/api/friend'
import MomentCard from '@/components/moments/MomentCard'
import PublishModal from '@/components/moments/PublishModal'
import { toast } from '@/components/ui/Toast'
import type { FeedItem } from '@/types/moment'



export default function MomentsPage() {
  const [items, setItems] = useState<FeedItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [showPublish, setShowPublish] = useState(false)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const scrollRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const updateUserProfiles = useChatStore((s) => s.updateUserProfiles)

  useEffect(() => {

    getFriendList().then((friends) => {
      const profiles: Record<string, { nickname: string; avatar?: string }> = {}
      for (const f of friends) {
        profiles[f.uid] = { nickname: f.nickname, avatar: f.avatar }
      }
      if (Object.keys(profiles).length > 0) {
        updateUserProfiles(profiles)
      }
    }).catch((e) => { console.error('Failed to preload friend profiles:', e) })
  }, [updateUserProfiles])

  const loadFeed = useCallback(async () => {
    setLoading(true)
    try {
      const feed = await momentApi.getFeed()
      setItems(feed ?? [])
      setHasMore((feed?.length ?? 0) >= 20)

      const uids = new Set((feed ?? []).map((f) => String(f.uid)))
      for (const uid of uids) loadUserProfile(uid)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [loadUserProfile])

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore || items.length === 0) return
    setLoadingMore(true)
    try {
      const lastId = items[items.length - 1].momentId
      const more = await momentApi.getFeed(lastId)
      if (!more || more.length === 0) {
        setHasMore(false)
      } else {
        setItems((prev) => [...prev, ...more])
        setHasMore(more.length >= 20)
        const uids = new Set(more.map((f) => String(f.uid)))
        for (const uid of uids) loadUserProfile(uid)
      }
    } catch {

    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, items, loadUserProfile])

  useEffect(() => {
    loadFeed()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 200) {
      loadMore()
    }
  }, [loadMore])

  const handlePublish = useCallback(async (content: string, visibility: string, showRange: string | null, media?: string) => {
    const body: { content: string; visibility?: string; showRange?: string; media?: string } = { content }
    if (media) body.media = media
    if (visibility !== 'public') body.visibility = visibility
    if (showRange) body.showRange = showRange
    await momentApi.publishMoment(body)
    loadFeed()
  }, [loadFeed])

  const handleDelete = useCallback((momentId: string) => {
    setItems((prev) => prev.filter((i) => i.momentId !== momentId))
    momentApi.deleteMoment(momentId)
      .then(() => toast('Deleted', 'success'))
      .catch(() => { loadFeed(); toast('Delete failed', 'error') })
  }, [loadFeed])

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="flex items-center justify-between px-5 py-3 bg-surface border-b border-border-light shrink-0">
        <h2 className="text-base font-semibold text-text-primary">Moments</h2>
        <button
          onClick={() => setShowPublish(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-primary-500 text-white text-sm font-medium rounded-lg
                     hover:bg-primary-400 active:bg-primary-600
                     transition-colors duration-200 cursor-pointer shadow-sm shadow-primary-500/25"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          New
        </button>
      </div>

      <div ref={scrollRef} onScroll={handleScroll} className="flex-1 overflow-y-auto bg-bg-primary">
        {loading && (
          <div className="flex items-center justify-center py-16">
            <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        )}

        {!loading && items.length === 0 && (
          <div className="flex items-center justify-center py-16">
            <div className="text-center">
              <div className="w-16 h-16 rounded-2xl bg-primary-100 flex items-center justify-center mx-auto mb-3">
                <svg className="w-8 h-8 text-primary-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <line x1="3" y1="9" x2="21" y2="9" />
                  <line x1="9" y1="21" x2="9" y2="9" />
                </svg>
              </div>
              <p className="text-sm text-text-secondary">No moments yet</p>
              <p className="text-xs text-text-secondary mt-1">Share your first moment</p>
            </div>
          </div>
        )}

        {items.map((item) => (
          <MomentCard key={item.momentId} item={item} onDelete={handleDelete} onAvatarClick={(uid: string) => navigate(`/moments/user/${uid}`)} />
        ))}

        {loadingMore && (
          <div className="flex items-center justify-center py-4">
            <svg className="animate-spin h-4 w-4 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        )}
      </div>

      <PublishModal
        open={showPublish}
        onClose={() => setShowPublish(false)}
        onSubmit={handlePublish}
      />
    </div>
  )
}
