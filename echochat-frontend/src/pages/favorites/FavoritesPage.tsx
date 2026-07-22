import { useState, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import * as favoriteApi from '@/api/favorite'
import { downloadFile } from '@/api/file'
import { useChatStore } from '@/stores/chatStore'
import { formatTime } from '@/utils/time'
import { toast } from '@/components/ui/Toast'
import type { Favorite } from '@/types/favorite'

function getTypeIcon(msgType: string) {
  switch (msgType) {
    case 'IMAGE': return { icon: '\uD83D\uDDBC', label: 'Image' }
    case 'VIDEO': return { icon: '\uD83C\uDFAC', label: 'Video' }
    case 'VOICE': return { icon: '\uD83C\uDFA4', label: 'Voice' }
    case 'FILE': return { icon: '\uD83D\uDCC4', label: 'File' }
    default: return { icon: '\uD83D\uDCAC', label: 'Text' }
  }
}

const typeFilters = [
  { key: '', label: 'All' },
  { key: 'TEXT', label: 'Text' },
  { key: 'IMAGE', label: 'Image' },
  { key: 'VIDEO', label: 'Video' },
  { key: 'VOICE', label: 'Voice' },
  { key: 'FILE', label: 'File' },
]

interface FavoriteItem extends Favorite {
  content?: string | null
}

function ImagePreview({ content }: { content: string }) {
  const [url, setUrl] = useState<string | null>(null)
  const [fullscreen, setFullscreen] = useState(false)

  useEffect(() => {
    try {
      const data = JSON.parse(content)
      const img = Array.isArray(data) ? data[0] : data
      if (img.fileId) downloadFile(img.fileId).then(setUrl).catch((e) => { console.error('Failed to download image for preview:', e) })
    } catch {  }
  }, [content])

  useEffect(() => {
    if (!fullscreen) return
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') setFullscreen(false) }
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [fullscreen])

  if (!url) return <div className="w-16 h-12 rounded bg-surface-active animate-pulse shrink-0" />
  return (
    <>
      <img src={url} alt="" className="w-16 h-12 rounded object-cover shrink-0 cursor-pointer hover:opacity-80 transition-opacity" onClick={() => setFullscreen(true)} />
      {fullscreen && createPortal(
        <div className="fixed inset-0 z-[300] bg-black/80 flex flex-col items-center justify-center animate-fade-in" onClick={() => setFullscreen(false)}>
          <img src={url} alt="" className="max-w-[95%] max-h-[85%] object-contain rounded-lg" onClick={(e) => e.stopPropagation()} />
          <div className="flex items-center gap-4 mt-4">
            <button onClick={() => setFullscreen(false)} className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer">Close</button>
            <a href={url} download className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer no-underline">Download</a>
          </div>
        </div>,
        document.body,
      )}
    </>
  )
}

function VideoPreview({ content }: { content: string }) {
  const [url, setUrl] = useState<string | null>(null)
  const [fullscreen, setFullscreen] = useState(false)

  useEffect(() => {
    try {
      const data = JSON.parse(content)
      if (data.fileId) downloadFile(data.fileId).then(setUrl).catch((e) => { console.error('Failed to download video for preview:', e) })
    } catch {  }
  }, [content])

  useEffect(() => {
    if (!fullscreen) return
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') setFullscreen(false) }
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [fullscreen])

  if (!url) return (
    <div className="w-16 h-12 rounded bg-surface-active animate-pulse shrink-0 flex items-center justify-center">
      <svg className="w-6 h-6 text-text-secondary" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
    </div>
  )

  return (
    <>
      <div className="relative w-16 h-12 rounded overflow-hidden shrink-0 cursor-pointer group" onClick={() => setFullscreen(true)}>
        <video src={url} className="w-full h-full object-cover" preload="metadata" />
        <div className="absolute inset-0 flex items-center justify-center bg-black/20 group-hover:bg-black/30">
          <svg className="w-6 h-6 text-white drop-shadow" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
        </div>
      </div>
      {fullscreen && createPortal(
        <div className="fixed inset-0 z-[300] bg-black/80 flex flex-col items-center justify-center animate-fade-in">
          <video src={url} controls autoPlay className="max-w-[95%] max-h-[85%] object-contain rounded-lg">Your browser does not support video playback.</video>
          <div className="flex items-center gap-4 mt-4">
            <button onClick={() => setFullscreen(false)} className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer">Close</button>
            <a href={url} download className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer no-underline">Download</a>
          </div>
        </div>,
        document.body,
      )}
    </>
  )
}

function VoicePreview({ content }: { content: string }) {
  const [url, setUrl] = useState<string | null>(null)
  const [playing, setPlaying] = useState(false)
  useEffect(() => {
    try {
      const data = JSON.parse(content)
      if (data.fileId) downloadFile(data.fileId).then(setUrl).catch((e) => { console.error('Failed to download voice for preview:', e) })
    } catch {  }
  }, [content])
  if (!url) return <span className="text-xs text-text-secondary shrink-0">[Voice]</span>
  return (
    <button
      onClick={() => {
        const a = new Audio(url)
        if (playing) { a.pause(); a.currentTime = 0; setPlaying(false) }
        else { a.play(); setPlaying(true); a.onended = () => setPlaying(false) }
      }}
      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium cursor-pointer ${
        playing ? 'bg-primary-500 text-white' : 'bg-primary-50 text-primary-600'
      }`}
    >
      {playing ? (
        <svg className="w-3 h-3 animate-pulse" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /></svg>
      ) : (
        <svg className="w-3 h-3" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
      )}
      Voice
    </button>
  )
}

export default function FavoritesPage() {
  const [favorites, setFavorites] = useState<FavoriteItem[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [batchDeleting, setBatchDeleting] = useState(false)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const loadUserProfile = useChatStore((s) => s.loadUserProfile)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await favoriteApi.getFavorites(filter || undefined)
      setFavorites(data ?? [])
      const uids = new Set<string>()
      for (const f of (data ?? [])) {
        if (f.fromUid) uids.add(String(f.fromUid))
      }
      for (const uid of uids) loadUserProfile(uid)
    } catch {
      toast('Failed to load favorites', 'error')
    } finally {
      setLoading(false)
    }
  }, [filter, loadUserProfile])

  useEffect(() => { load() }, [load])

  const handleDelete = async (msgId: string) => {
    try {
      await favoriteApi.removeFavorite(msgId)
      setFavorites((prev) => prev.filter((f) => String(f.msgId) !== msgId))
      setSelectedIds((prev) => { const next = new Set(prev); next.delete(msgId); return next })
      toast('Removed from favorites', 'success')
    } catch {
      toast('Failed to remove', 'error')
    }
  }

  const handleBatchDelete = async () => {
    if (selectedIds.size === 0) return
    setBatchDeleting(true)
    try {
      await favoriteApi.removeFavorites(Array.from(selectedIds))
      setFavorites((prev) => prev.filter((f) => !selectedIds.has(String(f.msgId))))
      setSelectedIds(new Set())
      toast(`Removed ${selectedIds.size} items`, 'success')
    } catch {
      toast('Batch delete failed', 'error')
    } finally {
      setBatchDeleting(false)
    }
  }

  const toggleSelect = (msgId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(msgId)) next.delete(msgId)
      else next.add(msgId)
      return next
    })
  }

  const selectAll = () => {
    if (selectedIds.size === favorites.length) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(favorites.map((f) => String(f.msgId))))
    }
  }

  const renderContent = (item: FavoriteItem) => {
    const raw = item.content || item.msgSummary
    if (!raw) return null
    if (item.msgType === 'TEXT') return <p className="text-sm text-text-primary line-clamp-3 break-all">{raw}</p>
    if (item.msgType === 'IMAGE') return <ImagePreview content={raw} />
    if (item.msgType === 'VOICE') return <VoicePreview content={raw} />
    if (item.msgType === 'VIDEO') return <VideoPreview content={raw} />
    if (item.msgType === 'FILE') {
      try {
        const d = JSON.parse(raw)
        const size = d.size || 0
        const sizeText = size > 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : size > 1024 ? `${(size / 1024).toFixed(0)} KB` : `${size} B`
        return <div className="flex items-center gap-2 text-xs text-text-secondary">
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><polyline points="13 2 13 9 20 9" /></svg>
          <span>{d.name || 'File'}</span>
          <span className="opacity-50">{sizeText}</span>
        </div>
      } catch { return <span className="text-xs text-text-secondary">[File]</span> }
    }
    return <p className="text-sm text-text-primary line-clamp-3 break-all">{raw}</p>
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-6 py-4 bg-surface border-b border-border-light shrink-0">
        <h2 className="text-base font-semibold text-text-primary mb-3">Favorites</h2>
        <div className="flex items-center gap-1 flex-wrap">
          {typeFilters.map((t) => (
            <button
              key={t.key}
              onClick={() => setFilter(t.key)}
              className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors cursor-pointer ${
                filter === t.key
                  ? 'bg-primary-500 text-white'
                  : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover'
              }`}
            >
              {t.label}
            </button>
          ))}
          <div className="flex-1" />
          {selectedIds.size > 0 && (
            <button
              onClick={handleBatchDelete}
              disabled={batchDeleting}
              className="px-3 py-1.5 text-xs font-medium bg-red-500 text-white rounded-lg hover:bg-red-400 disabled:opacity-40 transition-colors cursor-pointer"
            >
              {batchDeleting ? 'Deleting...' : `Delete (${selectedIds.size})`}
            </button>
          )}
          {favorites.length > 0 && (
            <button
              onClick={selectAll}
              className="px-3 py-1.5 text-xs font-medium rounded-lg border border-border-light text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors cursor-pointer"
            >
              {selectedIds.size === favorites.length ? 'Deselect All' : 'Select All'}
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex justify-center py-16">
            <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        ) : favorites.length === 0 ? (
          <div className="flex items-center justify-center py-20">
            <div className="text-center text-text-secondary">
              <div className="w-16 h-16 rounded-2xl bg-surface-active flex items-center justify-center mx-auto mb-3">
                <svg className="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <p className="text-sm">No favorites</p>
              <p className="text-xs mt-1">Right-click a message in chat to add it to favorites</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-border-light">
            {favorites.map((f) => {
              const typeInfo = getTypeIcon(f.msgType)
              const msgId = String(f.msgId)
              const isSelected = selectedIds.has(msgId)
              return (
                <div key={f.id} className={`flex items-center gap-3 px-6 py-3.5 hover:bg-surface-hover transition-colors group ${isSelected ? 'bg-primary-50 dark:bg-primary-900/20' : ''}`}>
                  <button
                    onClick={() => toggleSelect(msgId)}
                    className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 transition-colors cursor-pointer ${
                      isSelected ? 'bg-primary-500 border-primary-500' : 'border-border-light group-hover:border-primary-300'
                    }`}
                  >
                    {isSelected && <svg className="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>}
                  </button>
                  <div className="w-9 h-9 rounded-lg bg-surface-active flex items-center justify-center shrink-0 text-base">
                    {typeInfo.icon}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-[10px] font-medium text-primary-500 bg-primary-50 dark:bg-primary-900/20 rounded px-1.5 py-0.5 shrink-0">
                        {typeInfo.label}
                      </span>
                      {f.fromUid && (() => { const p = userProfiles[String(f.fromUid)]; const n = p?.memo || p?.nickname; return n ? (<span className="text-[10px] text-text-secondary truncate">from {n}</span>) : null })()}
                      <span className="text-[10px] text-text-secondary/50">{formatTime(f.collectedAt)}</span>
                    </div>
                    {renderContent(f)}
                  </div>
                  <button
                    onClick={() => handleDelete(String(f.msgId))}
                    className="shrink-0 p-1.5 rounded-lg text-text-secondary hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950 opacity-0 group-hover:opacity-100 transition-all cursor-pointer"
                    title="Remove from favorites"
                  >
                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
