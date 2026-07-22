import { useRef, useCallback } from 'react'
import { downloadFile } from '@/api/file'
import { useInView } from '@/hooks/useInView'

export default function FileMessage({ url, fileName, fileSize, fileId }: { url: string; fileName: string; fileSize: number; fileId?: string }) {
  const { ref, inView } = useInView('400px')
  const blobUrl = useRef<string | null>(null)
  const loading = useRef(false)

  const handleClick = useCallback(async (e: React.MouseEvent) => {
    if (blobUrl.current) return
    e.preventDefault()
    if (!fileId || loading.current) return
    loading.current = true
    try {
      const blob = await downloadFile(fileId)
      blobUrl.current = blob
      window.open(blob, '_blank')
    } catch {
      if (url) window.open(url, '_blank')
    } finally {
      loading.current = false
    }
  }, [fileId, url])

  const sizeText = fileSize > 1024 * 1024
    ? `${(fileSize / (1024 * 1024)).toFixed(1)} MB`
    : fileSize > 1024 ? `${(fileSize / 1024).toFixed(0)} KB` : `${fileSize} B`
  const ext = fileName.split('.').pop()?.toUpperCase() || 'FILE'

  return (
    <div ref={ref}>
      {inView && (
        <button onClick={handleClick} className="flex items-center gap-2.5 bg-white/10 rounded-lg px-3 py-2 cursor-pointer hover:bg-white/20 transition-colors w-full text-left">
          <div className="w-9 h-9 rounded-lg bg-white/20 flex items-center justify-center shrink-0">
            <span className="text-[10px] font-semibold">{ext.slice(0, 3)}</span>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium truncate max-w-[160px]">{fileName}</p>
            <p className="text-[10px] opacity-70">{sizeText}</p>
          </div>
          <svg className="w-4 h-4 shrink-0 opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" /></svg>
        </button>
      )}
    </div>
  )
}
