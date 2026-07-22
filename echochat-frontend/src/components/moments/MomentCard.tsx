import { useState, useCallback, useEffect, useRef, memo } from 'react'
import { createPortal } from 'react-dom'
import Avatar from '@/components/ui/Avatar'
import Modal from '@/components/ui/Modal'
import { formatTime } from '@/utils/time'
import { useChatStore } from '@/stores/chatStore'
import { useAuthStore } from '@/stores/authStore'
import * as momentApi from '@/api/moment'
import { downloadFile } from '@/api/file'
import EmojiPicker from '@/components/chat/EmojiPicker'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import { toast } from '@/components/ui/Toast'
import type { FeedItem, MomentComment } from '@/types/moment'

interface MediaInfo {
  fileId: string
  name: string
  size: number
  mimeType: string
}

interface MomentCardProps {
  item: FeedItem
  onDelete: (momentId: string) => void
  onAvatarClick?: (uid: string) => void
}

function MediaThumbnail({ fileId, name, mimeType, onClick, thumbUrl }: { fileId: string; name: string; mimeType: string; onClick?: () => void; thumbUrl?: string }) {
  const [resolved, setResolved] = useState<string | null>(null)
  const isVideo = mimeType.startsWith('video/')

  useEffect(() => {
    if (isVideo) return
    const ac = new AbortController()
    downloadFile(fileId).then((url) => {
      if (!ac.signal.aborted) setResolved(url)
    }).catch((e) => { console.error('Failed to download media thumbnail:', e) })
    return () => ac.abort()
  }, [fileId, isVideo])

  if (isVideo) {
    if (thumbUrl) {
      return (
        <button onClick={onClick} className="relative w-full h-full cursor-pointer group">
          <img src={thumbUrl} alt={name} className="w-full h-full object-cover" />
          <svg className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-6 h-6 text-white drop-shadow-lg group-hover:scale-110 transition-transform" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
        </button>
      )
    }
    return (
      <button onClick={onClick} className="relative w-full h-full flex items-center justify-center bg-surface-active dark:bg-gray-800 animate-pulse cursor-pointer group">
        <svg className="w-5 h-5 text-text-secondary/50" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
      </button>
    )
  }

  if (!resolved) {
    return (
      <div className="w-full h-full bg-surface-active dark:bg-gray-800 animate-pulse" />
    )
  }

  return (
    <button onClick={onClick} className="w-full h-full cursor-pointer">
      <img src={resolved} alt={name} className="w-full h-full object-cover" />
    </button>
  )
}

function generateVideoThumb(blobUrl: string): Promise<string | null> {
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.src = blobUrl
    video.currentTime = 1
    const onSeeked = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        canvas.getContext('2d')!.drawImage(video, 0, 0)
        resolve(canvas.toDataURL('image/jpeg', 0.5))
      } catch { resolve(null) }
    }
    video.addEventListener('seeked', onSeeked, { once: true })
    video.addEventListener('error', () => resolve(null), { once: true })
  })
}



export default function MomentCard({ item, onDelete, onAvatarClick }: MomentCardProps) {
  const uid = String(item.uid)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const author = useChatStore((s) => s.userProfiles[uid])
  const currentUid = useAuthStore((s) => s.user?.uid)
  const myAvatar = useAuthStore((s) => s.user?.avatar)
  const myName = useAuthStore((s) => s.user?.nickname)

  const [liked, setLiked] = useState(item.isLiked ?? false)
  const [likeCount, setLikeCount] = useState(item.likeCount ?? 0)
  const [comments, setComments] = useState<MomentComment[] | null>(null)
  const [showComments, setShowComments] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [replyTo, setReplyTo] = useState<{ uid: string; name: string } | null>(null)
  const [showEmoji, setShowEmoji] = useState(false)
  const [fullscreenIdx, setFullscreenIdx] = useState<number | null>(null)
  const [resolvedUrls, setResolvedUrls] = useState<Record<string, string>>({})
  const [videoThumbs, setVideoThumbs] = useState<Record<string, string>>({})
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [showDetail, setShowDetail] = useState(false)
  const commentInputRef = useRef<HTMLInputElement>(null)
  const emojiRef = useRef<HTMLDivElement>(null)

  let mediaList: MediaInfo[] = []
  try { mediaList = item.media ? JSON.parse(item.media) : [] } catch { mediaList = [] }

  useEffect(() => {
    if (uid && !author?.nickname) {
      loadUserProfile(uid)
    }
  }, [uid, author?.nickname, loadUserProfile])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && fullscreenIdx !== null) setFullscreenIdx(null)
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [fullscreenIdx])

  useEffect(() => {
    const toFetch = mediaList.filter((m) => m.fileId && !resolvedUrls[m.fileId])
    if (toFetch.length === 0) return
    const ac = new AbortController()
    for (const m of toFetch) {
      downloadFile(m.fileId)
        .then(async (url) => {
          if (ac.signal.aborted) return
          setResolvedUrls((prev) => ({ ...prev, [m.fileId]: url }))
          if (m.mimeType.startsWith('video/')) {
            const thumb = await generateVideoThumb(url)
            if (!ac.signal.aborted && thumb) setVideoThumbs((prev) => ({ ...prev, [m.fileId]: thumb }))
          }
        })
        .catch((e) => { console.error('Failed to download moments media:', e) })
    }
    return () => ac.abort()
  }, [item.momentId])

  const handleCommentEmoji = useCallback((emoji: string) => {
    const el = commentInputRef.current
    if (!el) { setCommentText((prev) => prev + emoji); return }
    const start = el.selectionStart ?? 0
    const end = el.selectionEnd ?? 0
    const before = commentText.substring(0, start)
    const after = commentText.substring(end)
    const newText = before + emoji + after
    setCommentText(newText)
    requestAnimationFrame(() => {
      const pos = start + emoji.length
      el.setSelectionRange(pos, pos)
      el.focus()
    })
    setShowEmoji(false)
  }, [commentText])

  const isOwn = String(currentUid) === uid

  const handleLike = useCallback(async () => {
    const prev = liked
    const prevCount = likeCount
    setLiked(!prev)
    setLikeCount(prev ? prevCount - 1 : prevCount + 1)
    try {
      if (prev) {
        await momentApi.unlikeMoment(item.momentId)
      } else {
        await momentApi.likeMoment(item.momentId)
      }
    } catch (err) {
      setLiked(prev)
      setLikeCount(prevCount)
      toast(err instanceof Error ? err.message : 'Failed to update like', 'error')
    }
  }, [item.momentId, liked, likeCount])

  const handleToggleComments = useCallback(async () => {
    if (showComments) {
      setShowComments(false)
      return
    }
    setShowComments(true)
    if (!comments) {
      try {
        const data = await momentApi.getComments(item.momentId)
        setComments(data)
      } catch {
        setComments([])
      }
    }
  }, [showComments, comments, item.momentId])

  const handleAddComment = useCallback(async () => {
    const trimmed = commentText.trim()
    if (!trimmed || submitting) return
    setSubmitting(true)
    try {
      const body: { content: string; replyToUid?: string } = { content: trimmed }
      if (replyTo) body.replyToUid = replyTo.uid
      const newComment = await momentApi.addComment(item.momentId, body)
      setComments((prev) => [...(prev ?? []), newComment])
      setCommentText('')
      setReplyTo(null)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to post comment', 'error')
    } finally {
      setSubmitting(false)
    }
  }, [commentText, submitting, replyTo, item.momentId])

  const handleDeleteComment = useCallback(async (commentId: number) => {
    try {
      await momentApi.deleteComment(commentId)
      setComments((prev) => (prev ?? []).filter((c) => c.id !== commentId))
    } catch {

    }
  }, [])

  const handleReplyClick = useCallback((uid: string) => {
    const profile = useChatStore.getState().userProfiles[uid]
    const name = (profile?.memo || profile?.nickname) || uid
    setReplyTo({ uid, name })
  }, [])

  const authorName = author?.memo || author?.nickname || uid
  const authorAvatar = author?.avatar

  return (
    <div className="bg-surface border-b border-border-light px-4 py-3 animate-fade-in">
      <div className="flex gap-2.5 items-start">
        <button onClick={() => onAvatarClick?.(uid)} className="shrink-0 cursor-pointer">
          <Avatar src={authorAvatar} fallback={authorName} size="sm" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 min-w-0">
              <button onClick={() => onAvatarClick?.(uid)} className="text-sm font-semibold text-text-primary truncate hover:text-primary-500 cursor-pointer transition-colors">
                {authorName}
              </button>
              <span className="text-[11px] text-text-secondary shrink-0">
                {formatTime(item.createdAt)}
              </span>
            </div>
            {isOwn && (
              <button
                onClick={() => setConfirmDelete(true)}
                className="text-xs text-text-secondary hover:text-red-500 px-1.5 py-0.5 rounded transition-colors cursor-pointer shrink-0"
              >
                Delete
              </button>
            )}
          </div>

          {item.content && (
            <p className="text-sm text-text-primary mt-1.5 whitespace-pre-wrap break-words leading-relaxed cursor-pointer hover:text-text-primary/80" onClick={() => setShowDetail(true)}>
            {item.content}
          </p>
          )}

          {mediaList.length > 0 && (
            <div className={`flex flex-wrap gap-1 mt-1.5 ${mediaList.length === 1 ? '' : ''}`}>
              {mediaList.map((m, idx) => (
                <div key={idx} className={`relative overflow-hidden rounded-md bg-surface-active border border-border-light ${
                  mediaList.length === 1 ? 'w-24 h-24' : 'w-20 h-20'
                }`}>
                  <MediaThumbnail
                    fileId={m.fileId}
                    name={m.name}
                    mimeType={m.mimeType}
                    onClick={() => setFullscreenIdx(idx)}
                    thumbUrl={videoThumbs[m.fileId]}
                  />
                </div>
              ))}
            </div>
          )}

          <div className="flex items-center gap-4 mt-2">
            <button
              onClick={handleLike}
              className={`flex items-center gap-1 text-xs transition-colors cursor-pointer ${
                liked ? 'text-red-500' : 'text-text-secondary hover:text-red-400'
              }`}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z" />
              </svg>
              {likeCount > 0 && <span>{likeCount}</span>}
            </button>

            <button
              onClick={handleToggleComments}
              className={`flex items-center gap-1 text-xs transition-colors cursor-pointer ${
                showComments ? 'text-primary-500' : 'text-text-secondary hover:text-primary-400'
              }`}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
              {!showComments && item.commentCount > 0 && <span>{item.commentCount}</span>}
            </button>
          </div>

          {showComments && (
            <div className="mt-2 bg-surface-active rounded-xl p-3">
              <div className="max-h-72 overflow-y-auto">
                {(comments ?? []).length === 0 ? (
                  <p className="text-xs text-text-secondary/60 py-1">No comments yet</p>
                ) : (
                  comments!.map((c) => (
                    <CommentItem
                      key={c.id}
                      comment={c}
                      currentUid={String(currentUid)}
                      momentOwnerUid={uid}
                      onReply={handleReplyClick}
                      onDelete={handleDeleteComment}
                    />
                  ))
                )}
              </div>

              {replyTo && (
                <div className="flex items-center gap-1 text-xs mt-2 pt-2 border-t border-gray-200/60">
                  <span className="text-text-secondary/70">Replying to</span>
                  <span className="text-primary-500 font-medium">@{replyTo.name}</span>
                  <button
                    onClick={() => setReplyTo(null)}
                    className="ml-1 text-text-secondary/50 hover:text-text-secondary cursor-pointer"
                  >
                    <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </button>
                </div>
              )}

              <div className="flex items-center gap-2 pt-2.5 mt-1.5 border-t border-gray-200/60">
                <Avatar src={myAvatar} fallback={myName || '?'} size="xs" className="shrink-0" />
                <input
                  ref={commentInputRef}
                  type="text"
                  value={commentText}
                  onChange={(e) => setCommentText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      handleAddComment()
                    }
                  }}
                  placeholder="Write a comment..."
                  className="flex-1 text-xs bg-surface border border-border-light rounded-full px-3.5 py-1.5 outline-none focus:ring-1 focus:ring-primary-200 transition-colors"
                />
                <div className="relative shrink-0" ref={emojiRef}>
                  <button
                    type="button"
                    onClick={() => setShowEmoji((v) => !v)}
                    className="w-6 h-6 flex items-center justify-center rounded text-text-secondary hover:text-primary-500 hover:bg-primary-50 transition-colors cursor-pointer"
                  >
                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="12" cy="12" r="10" /><path d="M8 14s1.5 2 4 2 4-2 4-2" /><line x1="9" y1="9" x2="9.01" y2="9" /><line x1="15" y1="9" x2="15.01" y2="9" />
                    </svg>
                  </button>
                  {showEmoji && (
                    <EmojiPicker triggerRef={emojiRef} onSelect={handleCommentEmoji} onClose={() => setShowEmoji(false)} align="right" />
                  )}
                </div>
                <button
                  onClick={handleAddComment}
                  disabled={!commentText.trim() || submitting}
                  className="text-xs text-primary-500 hover:text-primary-400 font-medium disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer whitespace-nowrap shrink-0"
                >
                  Send
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {fullscreenIdx !== null && createPortal(
        <div
          className="fixed inset-0 z-[300] bg-black/90 flex items-center justify-center animate-fade-in"
          onClick={() => setFullscreenIdx(null)}
        >
          <button
            onClick={() => setFullscreenIdx(null)}
            className="absolute top-4 right-4 w-10 h-10 flex items-center justify-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-colors cursor-pointer z-10"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
          </button>

          {mediaList.length > 1 && fullscreenIdx > 0 && (
            <button
              onClick={(e) => { e.stopPropagation(); setFullscreenIdx(fullscreenIdx - 1) }}
              className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-colors cursor-pointer z-10"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="15 18 9 12 15 6" /></svg>
            </button>
          )}
          {mediaList.length > 1 && fullscreenIdx < mediaList.length - 1 && (
            <button
              onClick={(e) => { e.stopPropagation(); setFullscreenIdx(fullscreenIdx + 1) }}
              className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-colors cursor-pointer z-10"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          )}

          {mediaList[fullscreenIdx]?.mimeType.startsWith('video/') ? (
            <video
              src={resolvedUrls[mediaList[fullscreenIdx].fileId] || undefined}
              controls
              autoPlay
              className="max-w-[90vw] max-h-[90vh] rounded-lg"
              onClick={(e) => e.stopPropagation()}
            />
          ) : (
            <img
              src={resolvedUrls[mediaList[fullscreenIdx].fileId] || undefined}
              alt={mediaList[fullscreenIdx].name}
              className="max-w-[90vw] max-h-[90vh] object-contain rounded-lg"
              onClick={(e) => e.stopPropagation()}
            />
          )}

          {mediaList.length > 1 && (
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-1.5">
              {mediaList.map((_, i) => (
                <span
                  key={i}
                  className={`w-1.5 h-1.5 rounded-full transition-colors ${i === fullscreenIdx ? 'bg-white' : 'bg-white/40'}`}
                />
              ))}
            </div>
          )}
        </div>,
        document.body,
      )}

      <ConfirmDialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => { onDelete(item.momentId); setConfirmDelete(false) }}
        title="Delete Moment"
        message="This action cannot be undone. Are you sure you want to delete this moment?"
        confirmLabel="Delete"
        danger
      />

      <Modal open={showDetail} onClose={() => setShowDetail(false)} title="Moment Detail" size="md">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3">
            <button onClick={() => onAvatarClick?.(uid)} className="shrink-0 cursor-pointer">
              <Avatar src={authorAvatar} fallback={authorName} size="sm" />
            </button>
            <div>
              <button onClick={() => onAvatarClick?.(uid)} className="text-sm font-semibold text-text-primary hover:text-primary-500 cursor-pointer transition-colors">
                {authorName}
              </button>
              <p className="text-[11px] text-text-secondary">{formatTime(item.createdAt)}</p>
            </div>
          </div>
          <p className="text-sm text-text-primary whitespace-pre-wrap break-words leading-relaxed">
            {item.content}
          </p>
          <div className="flex items-center gap-4 text-xs text-text-secondary border-t border-border-light pt-3">
            <span>{likeCount} likes</span>
            <span>{item.commentCount ?? (comments?.length ?? 0)} comments</span>
          </div>
        </div>
      </Modal>
    </div>
  )
}



const CommentItem = memo(function CommentItem({
  comment,
  currentUid,
  momentOwnerUid,
  onReply,
  onDelete,
}: {
  comment: MomentComment
  currentUid: string
  momentOwnerUid: string
  onReply: (uid: string) => void
  onDelete: (commentId: number) => void
}) {
  const cuid = String(comment.uid)
  const rcuid = comment.replyToUid ? String(comment.replyToUid) : null
  const profile = useChatStore((s) => s.userProfiles[cuid])
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)
  const commenterName = (profile?.memo || profile?.nickname) || cuid
  const commenterAvatar = profile?.avatar
  const replyToProfile = rcuid ? useChatStore((s) => s.userProfiles[rcuid]) : null
  const replyToName = (replyToProfile?.memo || replyToProfile?.nickname) || rcuid || ''
  const canDelete = cuid === currentUid || momentOwnerUid === currentUid

  useEffect(() => {
    if (!profile?.nickname) loadUserProfile(cuid)
    if (rcuid) {
      const replyProfile = useChatStore.getState().userProfiles[rcuid]
      if (!replyProfile?.nickname) loadUserProfile(rcuid)
    }
  }, [cuid, rcuid, profile?.nickname, loadUserProfile])

  return (
    <div className="flex gap-1.5 py-1 group">
      <Avatar src={commenterAvatar} fallback={commenterName} size="xs" className="mt-0.5 shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-xs leading-relaxed">
          <span className="font-medium text-primary-500">{commenterName}</span>
          {rcuid && (
            <>
              <span className="text-text-secondary/60 mx-1">reply</span>
              <span className="font-medium text-primary-500">{replyToName}</span>
            </>
          )}
          <span>：{comment.content}</span>
        </p>
        <div className="flex items-center gap-3 mt-0.5">
          <span className="text-[10px] text-text-secondary/50">{formatTime(comment.createdAt)}</span>
          {cuid !== currentUid && (
            <button onClick={() => onReply(cuid)} className="text-[10px] text-text-secondary/50 hover:text-primary-500 cursor-pointer transition-colors">
              Reply
            </button>
          )}
          {canDelete && (
            <button onClick={() => onDelete(comment.id)} className="text-[10px] text-text-secondary/50 hover:text-red-400 cursor-pointer transition-colors">
              Delete
            </button>
          )}
        </div>
      </div>
    </div>
  )
})
