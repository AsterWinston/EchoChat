import { useState, useRef, useCallback } from 'react'

export interface AudioBlob {
  blob: Blob
  url: string
  duration: number
}

export function useAudioRecorder(maxDuration = 60) {
  const [recording, setRecording] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [audio, setAudio] = useState<AudioBlob | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined)
  const startTimeRef = useRef(0)

  const cleanup = useCallback(() => {
    clearInterval(timerRef.current)
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop()
    }
    recorderRef.current = null
  }, [])

  const start = useCallback(async () => {
    setAudio(null)
    setElapsed(0)
    chunksRef.current = []
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream, { mimeType: MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm' })
      recorderRef.current = recorder

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }

      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        clearInterval(timerRef.current)
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType })
        const url = URL.createObjectURL(blob)
        const duration = Math.round((Date.now() - startTimeRef.current) / 1000)
        setRecording(false)
        setAudio({ blob, url, duration })
      }

      startTimeRef.current = Date.now()
      recorder.start(200)
      setRecording(true)

      timerRef.current = setInterval(() => {
        const elapsed = Math.round((Date.now() - startTimeRef.current) / 1000)
        setElapsed(elapsed)
        if (elapsed >= maxDuration) {
          cleanup()
        }
      }, 200)
    } catch {

    }
  }, [maxDuration, cleanup])

  const stop = useCallback(() => {
    cleanup()
  }, [cleanup])

  const clear = useCallback(() => {
    if (audio?.url) URL.revokeObjectURL(audio.url)
    setAudio(null)
  }, [audio])

  return { recording, elapsed, audio, start, stop, clear }
}
