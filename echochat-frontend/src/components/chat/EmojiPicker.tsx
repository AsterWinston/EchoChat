import { memo, useLayoutEffect, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const EMOJIS = [
  '😀','😂','🤣','😍','🥰','😘','😜','😎','🤩','😇',
  '😢','😡','🥺','😱','🤔','🙄','😴','🤗','🫡','😷',
  '👍','👎','👏','🙌','🤝','💪','✌️','🤞','👋','🤙',
  '❤️','🧡','💛','💚','💙','💜','🖤','🤍','💔','💖',
  '🔥','⭐','✨','🎉','🎊','🎂','🍰','☕','🍺','🎵',
  '🐶','🐱','🐼','🐨','🐰','🦊','🐸','🐵','🦁','🐮',
  '🌞','🌈','🌸','🌺','🍀','🌻','💐','🍁','🌊','⭐',
  '✅','❌','❓','❗','💯','🔒','🔑','💡','📌','📎',
]

interface EmojiPickerProps {
  onSelect: (emoji: string) => void
  onClose: () => void
  triggerRef: React.RefObject<HTMLElement | null>
  align?: 'left' | 'right'
}



export default memo(function EmojiPicker({ onSelect, onClose, triggerRef, align = 'left' }: EmojiPickerProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const isRight = align === 'right'

  useLayoutEffect(() => {
    const btn = triggerRef.current
    const panel = panelRef.current
    if (!btn || !panel) return
    const br = btn.getBoundingClientRect()
    const pw = panel.offsetWidth
    const ph = panel.offsetHeight
    const gap = 8

    let x = isRight ? br.right - pw : br.left
    let y = br.top - ph - gap

    if (y < 8) y = br.bottom + gap

    if (x < 8) x = 8
    if (x + pw > window.innerWidth - 8) x = window.innerWidth - pw - 8

    setPos({ x, y })
  }, [triggerRef, isRight])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const target = e.target as Node
      if (panelRef.current?.contains(target)) return
      if (triggerRef.current?.contains(target)) return
      onClose()
    }
    const keyHandler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('mousedown', handler)
    document.addEventListener('keydown', keyHandler)
    return () => {
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', keyHandler)
    }
  }, [onClose, triggerRef])

  return createPortal(
    <div
      ref={panelRef}
      className="fixed bg-surface rounded-xl shadow-lg border border-border-light p-2 w-60 z-[250] animate-scale-in"
      style={{ left: pos.x, top: pos.y }}
    >
      <div className="flex items-center justify-between px-1 mb-1.5">
        <span className="text-xs font-medium text-text-secondary">Emoji</span>
        <button onClick={onClose} className="text-text-secondary hover:text-text-primary cursor-pointer">
          <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
      <div className="grid grid-cols-8 gap-0.5 max-h-48 overflow-y-auto">
        {EMOJIS.map((emoji, i) => (
          <button
            key={i}
            onClick={() => onSelect(emoji)}
            className="w-6 h-6 flex items-center justify-center text-base rounded hover:bg-surface-hover active:bg-surface-active transition-colors cursor-pointer"
          >
            {emoji}
          </button>
        ))}
      </div>
    </div>,
    document.body,
  )
})
