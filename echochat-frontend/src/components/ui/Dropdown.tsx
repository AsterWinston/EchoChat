import { useState, useRef, useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface DropdownItem {
  label: string
  danger?: boolean
  onClick: () => void
}

interface DropdownProps {
  items: DropdownItem[]
  children: ReactNode
  align?: 'left' | 'right'
}

export default function Dropdown({ items, children, align = 'left' }: DropdownProps) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState({ x: 0, y: 0 })

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)
          && triggerRef.current && !triggerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    const keyHandler = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', handler)
    document.addEventListener('keydown', keyHandler)
    return () => {
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', keyHandler)
    }
  }, [open])

  const handleClick = () => {
    if (!triggerRef.current) return
    const rect = triggerRef.current.getBoundingClientRect()
    const x = align === 'right' ? rect.right - 192 : rect.left
    const y = rect.bottom + 4
    let adjustedX = x
    if (x + 192 > window.innerWidth - 4) adjustedX = window.innerWidth - 196
    if (adjustedX < 4) adjustedX = 4
    setPos({ x: adjustedX, y })
    setOpen((v) => !v)
  }

  return (
    <div ref={triggerRef} className="relative inline-block">
      <div onClick={handleClick} className="cursor-pointer">
        {children}
      </div>
      {open && createPortal(
        <div
          ref={menuRef}
          className="fixed z-[200] w-48 bg-surface rounded-xl shadow-lg border border-border-light py-1 animate-scale-in"
          style={{ left: pos.x, top: pos.y }}
        >
          {items.map((item, i) => (
            <button
              key={i}
              onClick={() => { item.onClick(); setOpen(false) }}
              className={`w-full text-left px-4 py-2.5 text-sm cursor-pointer hover:bg-surface-hover active:bg-surface-active transition-colors duration-100 ${
                item.danger ? 'text-red-500 hover:bg-red-50 dark:hover:bg-red-950' : 'text-text-primary'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </div>
  )
}
