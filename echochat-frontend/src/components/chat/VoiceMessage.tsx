import { useState, useRef, useEffect } from 'react'
import { downloadFile } from '@/api/file'

export default function VoiceMessage({ fileId, duration, isOwn }: { fileId?: string; duration?: number; isOwn?: boolean }) {
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [resolvedUrl, setResolvedUrl] = useState<string | null>(null)
  const [loadError, setLoadError] = useState(false)
  const audioRef = useRef<HTMLAudioElement>(null)
  const playPromiseRef = useRef<Promise<void> | null>(null)

  useEffect(() => {
    if (!fileId) return
    setLoadError(false)
    downloadFile(fileId)
      .then(setResolvedUrl)
      .catch(() => setLoadError(true))
  }, [fileId])

  useEffect(() => {
    return () => {
      if (resolvedUrl) URL.revokeObjectURL(resolvedUrl)
    }
  }, [resolvedUrl])

  useEffect(() => {
    const el = audioRef.current
    if (!el) return
    const tick = () => setCurrentTime(el.currentTime)
    el.addEventListener('timeupdate', tick)
    return () => el.removeEventListener('timeupdate', tick)
  }, [resolvedUrl])

  const handlePlay = () => {
    if (!audioRef.current) return
    if (playing) {
      audioRef.current.pause()
      audioRef.current.currentTime = 0
      setPlaying(false)
      setCurrentTime(0)
      playPromiseRef.current = null
    } else {
      const promise = audioRef.current.play()
      playPromiseRef.current = promise
      if (promise !== undefined) {
        promise
          .then(() => { setPlaying(true) })
          .catch((e) => {
            if (e.name !== 'AbortError') setPlaying(false)
          })
      } else {
        setPlaying(true)
      }
    }
  }

  const remaining = Math.max(0, (duration || 0) - currentTime)
  const mins = Math.floor(remaining / 60)
  const secs = Math.floor(remaining % 60)
  const durStr = `${mins}:${secs.toString().padStart(2, '0')}`

  const barHeights = playing
    ? [3, 6, 2, 8, 4, 7, 5, 9, 3, 6, 4, 8, 2, 7, 5]
    : [3, 5, 2, 6, 3, 7, 4, 5, 2, 6]

  return (
    <div className="flex items-center gap-2 min-w-[140px]">
      {loadError ? (
        <button
          onClick={() => { if (fileId) { setLoadError(false); downloadFile(fileId).then(setResolvedUrl).catch(() => setLoadError(true)) } }}
          className={`shrink-0 w-8 h-8 flex items-center justify-center rounded-full ${
            isOwn ? 'bg-white/20 text-white' : 'bg-primary-100 text-primary-600'
          } transition-colors cursor-pointer`}
          title="Retry loading voice"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
        </button>
      ) : (
        <button onClick={handlePlay} aria-label={playing ? 'Pause voice message' : 'Play voice message'} className={`shrink-0 w-8 h-8 flex items-center justify-center rounded-full ${
          isOwn
            ? (playing ? 'bg-white/30 text-white' : 'bg-white/20 text-white')
            : (playing ? 'bg-primary-500 text-white' : 'bg-primary-100 text-primary-600')
        } transition-colors cursor-pointer`}>
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill={playing ? 'none' : 'currentColor'} stroke={playing ? 'currentColor' : 'none'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            {playing ? (<><rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /></>) : (<polygon points="5 3 19 12 5 21 5 3" />)}
          </svg>
        </button>
      )}
      <div className="flex items-center gap-0.5">
        {barHeights.map((h, i) => (
          <div key={i} className={`w-0.5 rounded-full ${playing ? 'animate-bounce' : ''} ${
            isOwn ? (playing ? 'bg-white' : 'bg-white/70') : (playing ? 'bg-primary-500' : 'bg-gray-400')
          }`}
            style={{ height: `${h * 1.5}px`, animationDelay: playing ? `${i * 0.08}s` : undefined, animationDuration: '0.5s' }} />
        ))}
      </div>
      <span className={`text-[11px] font-mono ${isOwn ? 'text-white/70' : 'text-text-secondary'}`}>{durStr}</span>
      {resolvedUrl && <audio ref={audioRef} src={resolvedUrl} preload="auto" onEnded={() => { setPlaying(false); setCurrentTime(0) }} className="hidden" />}
    </div>
  )
}
