import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { downloadFile } from '@/api/file'
import { useInView } from '@/hooks/useInView'

export default function ImageMessage({ url, fileId, fileName }: { url: string; fileId?: string; fileName?: string }) {
  const [fullscreen, setFullscreen] = useState(false)
  const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined)
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
    if (!fullscreen) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setFullscreen(false) }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [fullscreen])

  const downloadName = fileName || 'image'

  return (
    <>
      <div ref={ref}>
        {!resolvedUrl ? (
          <div className="w-60 aspect-[4/3] bg-surface-active dark:bg-gray-800 animate-pulse rounded-lg" />
        ) : (
          <img src={resolvedUrl} alt="" className="w-60 aspect-[4/3] object-cover rounded-lg cursor-pointer animate-fade-in" onClick={() => setFullscreen(true)} />
        )}
      </div>
      {fullscreen && createPortal(
        <div role="dialog" aria-modal="true" aria-label="Image preview" className="absolute inset-0 z-[300] bg-black/80 flex flex-col items-center justify-center cursor-default animate-fade-in">
          <img src={resolvedUrl} alt="" className="max-w-[95%] max-h-[85%] object-contain rounded-lg cursor-pointer" onClick={(e) => e.stopPropagation()} />
          <div className="flex items-center gap-4 mt-4">
            <button onClick={() => setFullscreen(false)} aria-label="Close preview" className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60">Close</button>
            <a href={resolvedUrl} download={downloadName} aria-label="Download image" className="px-4 py-2 text-sm text-white/80 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors cursor-pointer no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60">Download</a>
          </div>
        </div>,
        document.getElementById('chat-area') || document.body,
      )}
    </>
  )
}
