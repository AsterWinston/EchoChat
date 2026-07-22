import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useChatStore } from '@/stores/chatStore'
import * as momentApi from '@/api/moment'
import Avatar from '@/components/ui/Avatar'
import type { FeedItem, MomentInfo } from '@/types/moment'
import MomentCard from '@/components/moments/MomentCard'
import { toast } from '@/components/ui/Toast'

function toFeedItem(m: MomentInfo): FeedItem {
  return {
    momentId: m.momentId,
    uid: m.uid,
    content: m.content,
    media: m.media,
    visibility: m.visibility,
    showRange: m.showRange,
    createdAt: m.createdAt,
    likeCount: m.likeCount ?? 0,
    commentCount: m.commentCount ?? 0,
    isLiked: m.isLiked ?? false,
  }
}



export default function UserMomentsPage() {
  const { uid } = useParams<{ uid: string }>()
  const navigate = useNavigate()
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const author = useChatStore((s) => (uid ? s.userProfiles[uid] : undefined))
  const [items, setItems] = useState<FeedItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (uid && !author?.nickname) loadUserProfile(uid)
  }, [uid, author?.nickname, loadUserProfile])

  const load = useCallback(async () => {
    if (!uid) return
    setLoading(true)
    try {
      const data = await momentApi.getUserMoments(uid)
      setItems((data ?? []).map(toFeedItem))
      setHasMore((data?.length ?? 0) >= 20)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [uid])

  const loadMore = useCallback(async () => {
    if (!uid || loadingMore || !hasMore || items.length === 0) return
    setLoadingMore(true)
    try {
      const lastId = items[items.length - 1].momentId
      const data = await momentApi.getUserMoments(uid, lastId)
      if (!data || data.length === 0) {
        setHasMore(false)
      } else {
        setItems((prev) => [...prev, ...data.map(toFeedItem)])
        setHasMore((data?.length ?? 0) >= 20)
      }
    } catch {

    } finally {
      setLoadingMore(false)
    }
  }, [uid, loadingMore, hasMore, items])

  useEffect(() => { load() }, [uid]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 200) loadMore()
  }, [loadMore])

  const handleDelete = useCallback((momentId: string) => {
    setItems((prev) => prev.filter((i) => i.momentId !== momentId))
    momentApi.deleteMoment(momentId)
      .then(() => toast('Deleted', 'success'))
      .catch(() => { load(); toast('Delete failed', 'error') })
  }, [load])

  const authorName = (author?.memo || author?.nickname) || uid || 'User'

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-5 py-3 bg-surface border-b border-border-light shrink-0 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="text-sm text-text-secondary hover:text-text-primary transition-colors cursor-pointer">
            Back
          </button>
          <div className="flex items-center gap-2">
            <Avatar src={author?.avatar} fallback={authorName} size="sm" />
            <span className="text-sm font-semibold text-text-primary">{authorName}'s Moments</span>
          </div>
        </div>
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
            <p className="text-sm text-text-secondary">No moments yet</p>
          </div>
        )}

        {items.map((item) => (
          <MomentCard key={item.momentId} item={item} onDelete={handleDelete} />
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
    </div>
  )
}
