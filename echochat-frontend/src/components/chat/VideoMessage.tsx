import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { downloadFile } from '@/api/file'
import { useInView } from '@/hooks/useInView'

export default function VideoMessage({ url, fileId, fileName }: { url: string; fileId?: string; fileName?: string }) {
  const [fullscreen, setFullscreen] = useState(false)
  const [playing, setPlaying] = useState(false)
  const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined)
  const [poster, setPoster] = useState<string | null>(null)
  const { ref, inView } = useInView('400px')

  useEffect(() => {
    if (!inView || resolvedUrl) return
    if (fileId) {
      downloadFile(fileId).then(setResolvedUrl).catch(() => setResolvedUrl(url || undefined))
    } else if (url) {
      setResolvedUrl(url)
    }
  }, [inView, fileId, url, resolvedUrl])

  useEffect(() => {
    if (!resolvedUrl || poster) return
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.src = resolvedUrl
    video.crossOrigin = 'anonymous'
    let cancelled = false
    const handle = () => {
      if (cancelled) return
      try {
        video.currentTime = 1
      } catch {}
    }
    video.addEventListener('loadeddata', handle)
    video.addEventListener('seeked', () => {
      if (cancelled) return
      try {
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth || 320
        canvas.height = video.videoHeight || 240
        const ctx = canvas.getContext('2d')
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
          setPoster(canvas.toDataURL('image/jpeg', 0.6))
        }
        video.remove()
      } catch {}
    })
    return () => { cancelled = true; video.remove() }
  }, [resolvedUrl, poster])

  useEffect(() => {
    if (!fullscreen) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setFullscreen(false) }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [fullscreen])

  const handleClick = () => {
    setPlaying(true)
    setFullscreen(true)
  }

  const downloadName = fileName || 'video'

  return (
    <>
      <div ref={ref}>
        {!resolvedUrl ? (
          <div className="w-60 aspect-[4/3] bg-surface-active dark:bg-gray-800 animate-pulse rounded-lg flex items-center justify-center">
            <svg className="w-8 h-8 text-text-secondary" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
          </div>
        ) : playing ? (
          <div className="relative w-60 aspect-[4/3] rounded-lg overflow-hidden cursor-pointer group animate-fade-in" onClick={() => setFullscreen(true)}>
            <video src={resolvedUrl} className="w-full h-full object-cover" controls preload="metadata" />
          </div>
        ) : (
          <div className="relative w-60 aspect-[4/3] rounded-lg overflow-hidden cursor-pointer group animate-fade-in bg-gray-700" onClick={handleClick}>
            {poster && <img src={poster} alt="" className="absolute inset-0 w-full h-full object-cover opacity-60 group-hover:opacity-40 transition-opacity" />}
            <div className="absolute inset-0 flex items-center justify-center">
              <svg className="w-12 h-12 text-white drop-shadow-lg opacity-80 group-hover:opacity-100 group-hover:scale-110 transition-all" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
            </div>
          </div>
        )}
      </div>
      {fullscreen && createPortal(
        <div role="dialog" aria-modal="true" aria-label="Video preview" className="absolute inset-0 z-[300] bg-black/80 flex flex-col items-center justify-center cursor-default animate-fade-in">
          <video src={resolvedUrl} controls autoPlay className="max-w-[95%] max-h-[85%] object-contain rounded-lg">Your browser does not support video playback.</video>
          <div className="flex items-center gap-4 mt-4">
            <button onClick={() => setFullscreen(false)} aria-label="Close preview" className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60">Close</button>
            <a href={resolvedUrl} download={downloadName} aria-label="Download video" className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60">Download</a>
          </div>
        </div>,
        document.getElementById('chat-area') || document.body,
      )}
    </>
  )
}
