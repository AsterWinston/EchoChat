import { useState, useRef, useCallback, useEffect, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useChatStore } from '@/stores/chatStore'
import { useAuthStore } from '@/stores/authStore'
import EmojiPicker from '@/components/chat/EmojiPicker'
import { useAudioRecorder } from '@/hooks/useAudioRecorder'
import { getGroupMembers } from '@/api/group'
import type { GroupMember } from '@/types/group'

interface PendingFile {
  file: File
  preview: string
}

interface ReplyInfo {
  msgId: string
  content: string
  nickname: string
}

interface ChatInputProps {
  onSend: (content: string, replyToMsgId?: string, msgType?: string) => void
  disabled?: boolean
  toUid?: string
  sessionType?: string
  targetId?: string
  replyTo?: ReplyInfo | null
  onCancelReply?: () => void
}

function getEditorText(editor: HTMLElement): string {
  let result = ''
  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      result += node.textContent || ''
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const el = node as HTMLElement
      if (el.dataset.uid) {
        result += `<@${el.dataset.uid}>`
      } else if (el.tagName === 'BR') {
        result += '\n'
      } else {
        for (const child of node.childNodes) walk(child)
      }
    }
  }
  for (const child of editor.childNodes) walk(child)
  return result
}

function getTextBeforeCursor(editor: HTMLElement): string {
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return ''
  const range = sel.getRangeAt(0).cloneRange()
  range.setStart(editor, 0)
  return range.toString().replace(/\u00A0/g, ' ')
}

export default function ChatInput({ onSend, disabled, toUid, sessionType, targetId, replyTo, onCancelReply }: ChatInputProps) {
  const [files, setFiles] = useState<PendingFile[]>([])
  const [sending, setSending] = useState(false)
  const sendingRef = useRef(false)
  const [showEmoji, setShowEmoji] = useState(false)
  const [voicePlaying, setVoicePlaying] = useState(false)
  const lastTypingSent = useRef(0)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const editorRef = useRef<HTMLDivElement>(null)
  const emojiRef = useRef<HTMLDivElement>(null)
  const voiceAudioRef = useRef<HTMLAudioElement>(null)
  const isComposing = useRef(false)
  const { recording, elapsed, audio, start: startRecord, stop: stopRecord, clear: clearAudio } = useAudioRecorder(60)

  const uploadAndSendFile = useChatStore((s) => s.uploadAndSendFile)
  const userProfiles = useChatStore((s) => s.userProfiles)
  const myUid = useAuthStore((s) => s.user?.uid)

  const [mentionOpen, setMentionOpen] = useState(false)
  const [mentionFilter, setMentionFilter] = useState('')
  const [mentionIndex, setMentionIndex] = useState(0)
  const [mentionMembers, setMentionMembers] = useState<GroupMember[]>([])
  const [, forceUpdate] = useState(0)
  const mentionRef = useRef<HTMLDivElement>(null)
  const popupRef = useRef<HTMLDivElement>(null)

  const isGroup = sessionType === 'group'

  const notifyTyping = () => {
    if (!toUid) return
    const now = Date.now()
    if (now - lastTypingSent.current < 2000) return
    lastTypingSent.current = now
    const ws = useChatStore.getState()._ws
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'typing', data: { toUid } }))
    }
  }

  const getContent = useCallback((): string => {
    const el = editorRef.current
    if (!el) return ''
    return getEditorText(el)
  }, [])

  const hasContent = useCallback((): boolean => {
    const el = editorRef.current
    if (!el) return false
    return el.textContent !== null && el.textContent.trim().length > 0
  }, [])

  const handleInput = useCallback(() => {
    const el = editorRef.current
    if (!el || isComposing.current) return

    forceUpdate(n => n + 1)

    const content = getEditorText(el)
    if (content) notifyTyping()

    if (!isGroup || !targetId) { setMentionOpen(false); return }

    const textBefore = getTextBeforeCursor(el)
    const atIdx = textBefore.lastIndexOf('@')

    if (atIdx >= 0) {
      const searchTerm = textBefore.substring(atIdx + 1)
      const hasSpaceBefore = atIdx === 0 || /\s/.test(textBefore[atIdx - 1])
      if (hasSpaceBefore && !searchTerm.includes(' ') && !searchTerm.includes('\u00A0')) {
        setMentionFilter(searchTerm.toLowerCase())
        setMentionIndex(0)
        setMentionOpen(true)
        loadMembers(searchTerm)
        return
      }
    }
    setMentionOpen(false)
  }, [isGroup, targetId])

  const loadMembers = useCallback(async (filter: string) => {
    if (!targetId) return
    try {
      const members = await getGroupMembers(targetId)
      const filtered = members.filter((m) => {
        if (String(m.uid) === String(myUid)) return false
        const profile = userProfiles[m.uid]
        const name = (profile?.nickname || m.nickname || m.uid).toLowerCase()
        return name.includes(filter.toLowerCase())
      })
      setMentionMembers(filtered)
    } catch {
      setMentionMembers([])
    }
  }, [targetId, myUid, userProfiles])

  useEffect(() => {
    if (!mentionOpen) return
    const handler = (e: MouseEvent) => {
      if (mentionRef.current && !mentionRef.current.contains(e.target as Node)
          && popupRef.current && !popupRef.current.contains(e.target as Node)) {
        setMentionOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [mentionOpen])

  const deleteAtWord = useCallback(() => {
    const sel = window.getSelection()
    if (!sel || !sel.rangeCount) return
    const range = sel.getRangeAt(0)
    const node = range.startContainer
    if (node.nodeType !== Node.TEXT_NODE) return
    const text = node.textContent || ''
    const offset = range.startOffset
    const atIdx = text.lastIndexOf('@', offset - 1)
    if (atIdx < 0) return

    const charBeforeAt = atIdx > 0 ? text[atIdx - 1] : ' '
    if (atIdx !== 0 && !/\s/.test(charBeforeAt)) return

    range.setStart(node, atIdx)
    range.deleteContents()

    const newRange = document.createRange()
    newRange.setStart(node, atIdx)
    newRange.setEnd(node, atIdx)
    sel.removeAllRanges()
    sel.addRange(newRange)
  }, [])

  const insertMentionSpan = useCallback((uid: string, displayName: string, isAll: boolean) => {
    const el = editorRef.current
    if (!el) return

    deleteAtWord()

    const sel = window.getSelection()
    if (!sel || !sel.rangeCount) return

    const span = document.createElement('span')
    span.contentEditable = 'false'
    span.dataset.uid = isAll ? 'all' : uid
    span.className = 'inline-flex items-center gap-0.5 bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300 rounded px-1.5 py-0.5 mx-0.5 text-[0.92em] font-medium select-none align-middle cursor-default'
    span.textContent = isAll ? '@Everyone' : `@${displayName}`

    const range = sel.getRangeAt(0)
    range.deleteContents()
    range.insertNode(span)

    const space = document.createTextNode('\u00A0')
    range.setStartAfter(span)
    range.insertNode(space)

    range.setStartAfter(space)
    range.setEndAfter(space)
    sel.removeAllRanges()
    sel.addRange(range)

    el.focus()
    setMentionOpen(false)
  }, [deleteAtWord])

  const insertMention = useCallback((uid: string) => {
    const profile = userProfiles[uid]
    const name = profile?.nickname || uid
    insertMentionSpan(uid, name, false)
  }, [userProfiles, insertMentionSpan])

  const insertMentionAll = useCallback(() => {
    insertMentionSpan('all', 'Everyone', true)
  }, [insertMentionSpan])

  const handlePickFiles = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleFilesSelected = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(e.target.files || [])
    if (selected.length === 0) return
    const newFiles: PendingFile[] = []
    for (const f of selected) {
      newFiles.push({
        file: f,
        preview: f.type.startsWith('image/') || f.type.startsWith('video/') ? URL.createObjectURL(f) : '',
      })
    }
    setFiles((prev) => [...prev, ...newFiles])
    if (fileInputRef.current) fileInputRef.current.value = ''
  }, [])

  const handleRemoveFile = useCallback((idx: number) => {
    setFiles((prev) => {
      const item = prev[idx]
      if (item?.preview && item.preview.startsWith('blob:')) URL.revokeObjectURL(item.preview)
      return prev.filter((_, i) => i !== idx)
    })
  }, [])

  const insertTextAtCursor = useCallback((text: string) => {
    const sel = window.getSelection()
    if (!sel || !sel.rangeCount) return
    const range = sel.getRangeAt(0)
    range.deleteContents()
    const textNode = document.createTextNode(text)
    range.insertNode(textNode)
    range.setStartAfter(textNode)
    range.setEndAfter(textNode)
    sel.removeAllRanges()
    sel.addRange(range)
    editorRef.current?.focus()
  }, [])

  const handleEmojiSelect = useCallback((emoji: string) => {
    insertTextAtCursor(emoji)
    setShowEmoji(false)
    handleInput()
  }, [insertTextAtCursor, handleInput])

  const clearEditor = useCallback(() => {
    const el = editorRef.current
    if (!el) return
    el.innerHTML = ''
    el.focus()
  }, [])

  const submit = useCallback(async () => {
    const text = getContent().trim()
    const hasFiles = files.length > 0
    const hasVoice = audio !== null
    if ((!text && !hasFiles && !hasVoice) || disabled || sendingRef.current) return

    sendingRef.current = true
    setSending(true)
    setMentionOpen(false)
    try {
      if (audio) {
        const file = new File([audio.blob], `voice-${Date.now()}.webm`, { type: audio.blob.type })
        uploadAndSendFile(file, { duration: audio.duration })
        clearAudio()
      }
      for (const f of files) {
        uploadAndSendFile(f.file)
      }
      if (text) {
        onSend(text, replyTo?.msgId)
      }
      clearEditor()
      setFiles((prev) => {
        for (const f of prev) {
          if (f.preview && f.preview.startsWith('blob:')) URL.revokeObjectURL(f.preview)
        }
        return []
      })
      lastTypingSent.current = 0
      onCancelReply?.()
    } finally {
      sendingRef.current = false
      setSending(false)
    }
  }, [getContent, files, audio, disabled, onSend, replyTo, onCancelReply, uploadAndSendFile, clearAudio, clearEditor])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    submit()
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (mentionOpen) {
      const filtered = getAllMentionOptions()
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setMentionIndex((prev) => (prev + 1) % filtered.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setMentionIndex((prev) => (prev - 1 + filtered.length) % filtered.length)
        return
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        const selected = filtered[mentionIndex]
        if (selected) {
          if (selected.type === 'all') {
            insertMentionAll()
          } else {
            insertMention(selected.uid)
          }
        }
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setMentionOpen(false)
        return
      }
      return
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
    if (e.key === 'Backspace' && isGroup) {
      const sel = window.getSelection()
      if (sel && sel.rangeCount > 0) {
        const range = sel.getRangeAt(0)
        const node = range.startContainer
        if (node.nodeType === Node.TEXT_NODE && range.startOffset === 0 && node.previousSibling) {
          const prev = node.previousSibling
          if (prev.nodeType === Node.ELEMENT_NODE && (prev as HTMLElement).dataset.uid) {
            e.preventDefault()
            ;(prev as ChildNode).remove()
            if (node.textContent === '\u00A0') { (node as ChildNode).remove() }
          }
        }
      }
    }
  }

  const handlePaste = useCallback((e: React.ClipboardEvent<HTMLDivElement>) => {
    const items = e.clipboardData?.items
    if (!items) return
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (item.type.startsWith('image/')) {
        e.preventDefault()
        const blob = item.getAsFile()
        if (blob) {
          const preview = URL.createObjectURL(blob)
          setFiles((prev) => [...prev, { file: blob, preview }])
        }
        return
      }
    }
    e.preventDefault()
    const text = e.clipboardData?.getData('text/plain') || ''
    if (text) { insertTextAtCursor(text); handleInput() }
  }, [insertTextAtCursor, handleInput])

  const getAllMentionOptions = () => {
    const options: Array<{ type: 'member'; uid: string; nickname?: string } | { type: 'all' }> = [
      { type: 'all' as const },
      ...mentionMembers.map((m) => ({ type: 'member' as const, uid: m.uid, nickname: m.nickname })),
    ]
    return options
  }

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60)
    const sec = s % 60
    return `${m}:${sec.toString().padStart(2, '0')}`
  }

  const editorEmpty = !hasContent()
  const canSubmit = (!editorEmpty || files.length > 0 || audio !== null) && !disabled && !sending

  return (
    <form onSubmit={handleSubmit} className="flex flex-col bg-surface border-t border-border-light">
      {replyTo && (
        <div className="flex items-center gap-2 px-4 pt-3 pb-0">
          <div className="flex-1 min-w-0 flex items-center gap-2 bg-primary-50 rounded-lg px-3 py-2 border-l-2 border-primary-400">
            <svg className="w-3.5 h-3.5 text-primary-500 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="9 17 4 12 9 7" />
              <path d="M20 18v-2a4 4 0 0 0-4-4H4" />
            </svg>
            <div className="min-w-0">
              <span className="text-xs font-medium text-primary-600">Replying to {replyTo.nickname}</span>
              <p className="text-xs text-primary-500 truncate">{replyTo.content}</p>
            </div>
          </div>
          <button type="button" onClick={onCancelReply} className="shrink-0 p-1 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors cursor-pointer">
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      )}

      {audio && (
        <div className="flex items-center gap-2 px-4 pt-3 pb-0">
          <div className="flex items-center gap-2 bg-surface-active rounded-lg px-3 py-2 flex-1 min-w-0">
            <button
              type="button"
              onClick={() => {
                if (!audio) return
                const el = voiceAudioRef.current
                if (!el) return
                if (voicePlaying) { el.pause(); el.currentTime = 0; setVoicePlaying(false) }
                else { el.play(); setVoicePlaying(true) }
              }}
              className={`shrink-0 w-8 h-8 flex items-center justify-center rounded-full ${
                voicePlaying ? 'bg-primary-500 text-white' : 'bg-primary-100 text-primary-600'
              } transition-colors cursor-pointer`}
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill={voicePlaying ? 'none' : 'currentColor'} stroke={voicePlaying ? 'currentColor' : 'none'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                {voicePlaying ? (
                  <><rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /></>
                ) : (
                  <polygon points="5 3 19 12 5 21 5 3" />
                )}
              </svg>
            </button>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                {voicePlaying ? (
                  <div className="flex items-center gap-0.5">
                    {[3, 6, 2, 8, 4, 7, 5].map((h, i) => (
                      <div key={i} className={`w-0.5 bg-primary-500 rounded-full animate-bounce`}
                        style={{ height: `${h * 2}px`, animationDelay: `${i * 0.1}s`, animationDuration: '0.6s' }} />
                    ))}
                  </div>
                ) : (
                  <span className="text-xs text-text-primary font-mono">{formatTime(audio.duration)}</span>
                )}
                <span className="text-[10px] text-text-secondary">Voice message</span>
              </div>
            </div>
            <button
              type="button"
              onClick={clearAudio}
              className="shrink-0 p-1 rounded text-text-secondary hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <audio ref={voiceAudioRef} src={audio.url} onEnded={() => setVoicePlaying(false)} className="hidden" />
        </div>
      )}

      {files.length > 0 && (
        <div className="flex items-center gap-2 px-4 pt-3 pb-0 overflow-x-auto">
          {files.map((f, i) => (
            <div key={i} className="relative shrink-0 group">
              {f.preview ? (
                <div className="w-16 h-16 rounded-lg overflow-hidden bg-surface-active border border-border-light">
                  {f.file.type.startsWith('video/') ? (
                    <div className="w-full h-full flex items-center justify-center bg-surface-active dark:bg-gray-800">
                      <svg className="w-6 h-6 text-text-secondary" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
                    </div>
                  ) : (
                    <img src={f.preview} alt="" className="w-full h-full object-cover" />
                  )}
                </div>
              ) : (
                <div className="w-16 h-16 rounded-lg bg-surface-active border border-border-light flex flex-col items-center justify-center gap-0.5">
                  <svg className="w-5 h-5 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><polyline points="13 2 13 9 20 9" /></svg>
                  <span className="text-[9px] text-text-secondary truncate max-w-[56px]">{f.file.name.split('.').pop()?.toUpperCase()}</span>
                </div>
              )}
              <button
                type="button"
                onClick={() => handleRemoveFile(i)}
                className="absolute -top-1.5 -right-1.5 w-4 h-4 bg-surface border border-border-light rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
              >
                <svg className="w-2.5 h-2.5 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center gap-3 p-4">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar"
          className="hidden"
          onChange={handleFilesSelected}
        />
        <button
          type="button"
          onClick={handlePickFiles}
          disabled={disabled || sending}
          aria-label="Attach file"
          title="Attach file"
          className="shrink-0 w-9 h-9 flex items-center justify-center rounded-xl text-text-secondary hover:text-primary-500 hover:bg-primary-50 transition-colors cursor-pointer disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
          </svg>
        </button>
        <div className="relative shrink-0" ref={emojiRef}>
          <button
            type="button"
            onClick={() => setShowEmoji((v) => !v)}
            aria-label="Emoji picker"
            title="Emoji picker"
            className="w-9 h-9 flex items-center justify-center rounded-xl text-text-secondary hover:text-primary-500 hover:bg-primary-50 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" /><path d="M8 14s1.5 2 4 2 4-2 4-2" /><line x1="9" y1="9" x2="9.01" y2="9" /><line x1="15" y1="9" x2="15.01" y2="9" />
            </svg>
          </button>
          {showEmoji && (
            <EmojiPicker triggerRef={emojiRef} onSelect={handleEmojiSelect} onClose={() => setShowEmoji(false)} />
          )}
        </div>
        {recording ? (
          <div className="flex items-center gap-2 flex-1 bg-surface-active rounded-xl px-4 py-2.5" style={{ minHeight: '42px' }}>
            <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
            <span className="text-sm text-red-500 font-mono">{formatTime(elapsed)}</span>
            <span className="text-xs text-text-secondary">Recording...</span>
            <div className="flex-1" />
            <button
              type="button"
              onClick={stopRecord}
              className="shrink-0 w-8 h-8 flex items-center justify-center rounded-full bg-red-500 text-white hover:bg-red-400 transition-colors cursor-pointer"
              title="Stop recording"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="6" width="12" height="12" rx="1" />
              </svg>
            </button>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={startRecord}
              disabled={disabled || sending}
              className="shrink-0 w-9 h-9 flex items-center justify-center rounded-xl text-text-secondary hover:text-primary-500 hover:bg-primary-50 transition-colors cursor-pointer disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
              aria-label="Record voice message"
              title="Record voice"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" /><line x1="8" y1="23" x2="16" y2="23" />
              </svg>
            </button>
            <div ref={mentionRef} className="flex-1 relative">
              <div
                ref={editorRef}
                contentEditable
                suppressContentEditableWarning
                role="textbox"
                aria-multiline="true"
                aria-label={isGroup ? 'Type a message, @ to mention' : 'Type a message'}
                onInput={handleInput}
                onKeyDown={handleKeyDown}
                onPaste={handlePaste}
                onCompositionStart={() => { isComposing.current = true }}
                onCompositionEnd={() => { isComposing.current = false; handleInput() }}
                data-placeholder={isGroup ? (replyTo ? 'Type a reply...' : 'Type a message, @ to mention') : (replyTo ? 'Type a reply...' : 'Type a message...')}
                className="w-full bg-surface-active rounded-xl px-4 py-2.5 text-sm text-text-primary
                           placeholder:text-text-secondary/50
                           ring-2 ring-transparent focus:ring-primary-100 focus:bg-surface focus:outline-none
                           transition-colors duration-200
                           max-h-32 overflow-y-auto"
                style={{ minHeight: '42px', wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}
              />
              {mentionOpen && createPortal(
                <div
                  ref={popupRef}
                  className="fixed z-[150] w-56 max-h-48 overflow-y-auto bg-surface rounded-xl shadow-lg border border-border-light py-1 animate-scale-in"
                  style={(() => {
                    if (!mentionRef.current) return { left: 0, bottom: 0 }
                    const rect = mentionRef.current.getBoundingClientRect()
                    return { left: rect.left, bottom: window.innerHeight - rect.top + 8 }
                  })()}
                >
                  {(() => {
                    const allOptions = getAllMentionOptions()
                    if (allOptions.length === 1 && allOptions[0].type === 'all' && mentionFilter.length > 0) {
                      return <div className="px-3 py-4 text-xs text-text-secondary text-center">No members found</div>
                    }
                    return allOptions.map((opt, i) => {
                      if (opt.type === 'all') {
                        return (
                          <button
                            key="all"
                            type="button"
                            className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2.5 cursor-pointer transition-colors ${
                              i === mentionIndex ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600' : 'text-text-primary hover:bg-surface-hover'
                            }`}
                            onClick={() => insertMentionAll()}
                            onMouseEnter={() => setMentionIndex(i)}
                          >
                            <span className="w-7 h-7 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center text-xs font-bold text-primary-600 shrink-0">
                              @
                            </span>
                            <span className="font-medium">Everyone</span>
                          </button>
                        )
                      }
                      const profile = userProfiles[opt.uid]
                      const name = profile?.nickname || opt.nickname || opt.uid
                      return (
                        <button
                          key={opt.uid}
                          type="button"
                          className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2.5 cursor-pointer transition-colors ${
                            i === mentionIndex ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600' : 'text-text-primary hover:bg-surface-hover'
                          }`}
                          onClick={() => insertMention(opt.uid)}
                          onMouseEnter={() => setMentionIndex(i)}
                        >
                          <span className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0 ${
                            profile?.avatar ? '' : 'bg-primary-400'
                          }`}>
                            {profile?.avatar ? (
                              <img src={profile.avatar} alt="" className="w-7 h-7 rounded-full object-cover" />
                            ) : (
                              name.charAt(0).toUpperCase()
                            )}
                          </span>
                          <span>{name}</span>
                        </button>
                      )
                    })
                  })()}
                </div>,
                document.body,
              )}
            </div>
          </>
        )}
        <button
          type="submit"
          disabled={!canSubmit}
          className="shrink-0 w-10 h-10 flex items-center justify-center rounded-xl
                     bg-primary-500 text-white
                     hover:bg-primary-400 active:bg-primary-600
                     disabled:opacity-40 disabled:cursor-not-allowed
                     transition-all duration-200 cursor-pointer shadow-sm shadow-primary-500/25"
        >
          {sending ? (
            <svg className="w-5 h-5 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          ) : (
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          )}
        </button>
      </div>
    </form>
  )
}
