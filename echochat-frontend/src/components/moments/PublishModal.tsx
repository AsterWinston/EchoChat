import { useRef, useState, useCallback, useEffect } from 'react'
import Modal from '@/components/ui/Modal'
import { uploadFile } from '@/utils/upload'
import { getFriendList } from '@/api/friend'
import type { FriendInfo } from '@/types/friend'

interface MediaItem {
  name: string
  size: number
  mimeType: string
  preview: string
}

interface PublishModalProps {
  open: boolean
  onClose: () => void
  onSubmit: (content: string, visibility: string, showRange: string | null, media?: string, blockUids?: string[]) => Promise<void>
}

const SHOW_RANGE_OPTIONS = [
  { value: '', label: 'All' },
  { value: '3d', label: 'Last 3 days' },
  { value: '30d', label: 'Last 30 days' },
  { value: '180d', label: 'Last 6 months' },
]

const MAX_MEDIA = 9

export default function PublishModal({ open, onClose, onSubmit }: PublishModalProps) {
  const [text, setText] = useState('')
  const [visibility, setVisibility] = useState('public')
  const [showRange, setShowRange] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [mediaList, setMediaList] = useState<MediaItem[]>([])
  const [friends, setFriends] = useState<FriendInfo[]>([])
  const [blockUids, setBlockUids] = useState<string[]>([])
  const [friendSearch, setFriendSearch] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      getFriendList().then(setFriends).catch(() => setFriends([]))
    }
  }, [open])

  const handlePickFiles = () => fileInputRef.current?.click()

  const handleFilesSelected = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || [])
    if (files.length === 0) return
    setMediaList((prev) => {
      const remaining = MAX_MEDIA - prev.length
      const toAdd = files.slice(0, remaining).filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
      return [...prev, ...toAdd.map((f) => ({
        name: f.name,
        size: f.size,
        mimeType: f.type,
        preview: URL.createObjectURL(f),
      }))]
    })
    if (fileInputRef.current) fileInputRef.current.value = ''
  }, [])

  const handleRemoveMedia = (idx: number) => {
    setMediaList((prev) => {
      URL.revokeObjectURL(prev[idx].preview)
      return prev.filter((_, i) => i !== idx)
    })
  }

  const reset = () => {
    setText('')
    setVisibility('public')
    setShowRange('')
    setBlockUids([])
    setFriendSearch('')
    setMediaList((prev) => {
      for (const m of prev) URL.revokeObjectURL(m.preview)
      return []
    })
  }

  const uploadAll = useCallback(async (items: MediaItem[]): Promise<string[]> => {
    const fileIds: string[] = []
    setUploading(true)
    setUploadProgress(0)
    const total = items.length
    for (let i = 0; i < total; i++) {
      const item = items[i]
      try {
        const blob = await fetch(item.preview).then((r) => r.blob())
        const file = new File([blob], item.name, { type: item.mimeType })
        const result = await uploadFile(file, {
          onProgress: (pct) => setUploadProgress(Math.round((i * 100 + pct) / total)),
        })
        const fileId = JSON.parse(result.content).fileId || JSON.parse(result.content)[0]?.fileId || ''
        fileIds.push(fileId)
      } catch {
        fileIds.push('')
      }
      setUploadProgress(Math.round(((i + 1) / total) * 100))
    }
    setUploading(false)
    return fileIds
  }, [])

  const handleSubmit = async () => {
    const trimmed = text.trim()
    if ((!trimmed && mediaList.length === 0) || submitting) return
    setSubmitting(true)
    try {
      let media: string | undefined
      if (mediaList.length > 0) {
        const fileIds = await uploadAll(mediaList)
        const valid = mediaList
          .map((m, i) => ({ ...m, fileId: fileIds[i] }))
          .filter((m) => m.fileId)
        if (valid.length > 0) {
          media = JSON.stringify(valid.map((m) => ({
            fileId: m.fileId,
            name: m.name,
            size: m.size,
            mimeType: m.mimeType,
          })))
        }
      }
      await onSubmit(trimmed, visibility, showRange || null, media, visibility === 'restricted' ? blockUids : undefined)
      reset()
      onClose()
    } catch {
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={() => { reset(); onClose() }} title="New Moment" size="md">
      <div className="flex flex-col gap-3 max-h-[70vh] overflow-y-auto">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*"
          className="hidden"
          onChange={handleFilesSelected}
        />

        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What's on your mind?"
          rows={3}
          className="w-full resize-none bg-gray-50 rounded-xl px-4 py-3 text-sm text-text-primary
                     placeholder:text-text-secondary/50
                     ring-2 ring-transparent focus:ring-primary-100 focus:bg-white focus:outline-none
                     transition-colors duration-200"
        />

        {mediaList.length > 0 && (
          <div className="grid grid-cols-3 gap-1.5 max-h-[28vh] overflow-y-auto p-0.5">
            {mediaList.map((item, idx) => (
              <div key={idx} className="relative aspect-square rounded-lg overflow-hidden bg-surface-active border border-border-light group">
                {item.mimeType.startsWith('video/') ? (
                  <div className="w-full h-full relative flex items-center justify-center bg-black">
                    <video src={item.preview} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 min-w-full min-h-full opacity-70" muted preload="metadata" />
                    <svg className="w-8 h-8 text-white drop-shadow-lg relative z-10" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
                  </div>
                ) : (
                  <img src={item.preview} alt="" className="w-full h-full object-cover" />
                )}
                <button
                  onClick={() => handleRemoveMedia(idx)}
                  className="absolute top-1 right-1 w-5 h-5 bg-black/50 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                >
                  <svg className="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                </button>
                {uploading && (
                  <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                    <span className="text-xs text-white font-medium">{uploadProgress}%</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {mediaList.length < MAX_MEDIA && (
          <button
            onClick={handlePickFiles}
            className="flex items-center justify-center gap-1.5 py-2 border-2 border-dashed border-border-light rounded-xl text-xs text-text-secondary hover:text-primary-500 hover:border-primary-300 transition-colors cursor-pointer shrink-0"
          >
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" /></svg>
            <span>{mediaList.length === 0 ? 'Add photos/videos' : `Add more (${mediaList.length}/${MAX_MEDIA})`}</span>
          </button>
        )}

        <div className="flex items-center gap-3">
          <span className="text-xs text-text-secondary">Visibility:</span>
          <div className="flex items-center bg-surface-active rounded-lg p-0.5">
            <button
              onClick={() => setVisibility('public')}
              className={`px-3 py-1 text-xs rounded-md transition-colors cursor-pointer ${
                visibility === 'public' ? 'bg-surface text-text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              Public
            </button>
            <button
              onClick={() => setVisibility('restricted')}
              className={`px-3 py-1 text-xs rounded-md transition-colors cursor-pointer ${
                visibility === 'restricted' ? 'bg-surface text-text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary'
              }`}
            >
              Restricted
            </button>
          </div>
        </div>

        {visibility === 'restricted' && (
          <div className="flex flex-col gap-1.5">
            <span className="text-xs text-text-secondary">Block specific friends:</span>
            <input
              value={friendSearch}
              onChange={(e) => setFriendSearch(e.target.value)}
              placeholder="Search friends..."
              className="w-full text-xs bg-gray-50 rounded-lg px-3 py-1.5 border-none outline-none text-text-primary"
            />
            <div className="max-h-28 overflow-y-auto flex flex-col gap-0.5">
              {friends
                .filter((f) => !friendSearch || f.nickname?.toLowerCase().includes(friendSearch.toLowerCase()) || f.uid.includes(friendSearch))
                .map((f) => {
                  const checked = blockUids.includes(f.uid)
                  return (
                    <label key={f.uid} className="flex items-center gap-2 px-1 py-0.5 rounded cursor-pointer hover:bg-gray-50">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => {
                          setBlockUids((prev) => checked ? prev.filter((id) => id !== f.uid) : [...prev, f.uid])
                        }}
                        className="accent-primary-500"
                      />
                      <span className="text-xs text-text-primary truncate">{f.nickname || f.uid}</span>
                      {f.memo && <span className="text-[10px] text-text-secondary truncate">({f.memo})</span>}
                    </label>
                  )
                })}
              {friends.length === 0 && <span className="text-xs text-text-secondary px-1">No friends</span>}
            </div>
          </div>
        )}

        <div className="flex items-center gap-3">
          <span className="text-xs text-text-secondary">Show range:</span>
          <select
            value={showRange}
            onChange={(e) => setShowRange(e.target.value)}
            className="text-xs bg-gray-100 rounded-lg px-3 py-1.5 border-none outline-none text-text-primary cursor-pointer"
          >
            {SHOW_RANGE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <button
            onClick={() => { reset(); onClose() }}
            className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary rounded-lg hover:bg-gray-100 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={(!text.trim() && mediaList.length === 0) || submitting}
            className="px-5 py-2 text-sm font-medium bg-primary-500 text-white rounded-lg
                       hover:bg-primary-400 active:bg-primary-600
                       disabled:opacity-40 disabled:cursor-not-allowed
                       transition-all duration-200 cursor-pointer shadow-sm shadow-primary-500/25"
          >
            {submitting
              ? (uploading ? `Uploading ${uploadProgress}%` : 'Posting...')
              : 'Post'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
