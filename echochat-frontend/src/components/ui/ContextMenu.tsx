import { useEffect, useLayoutEffect, useRef, useState, type ReactNode, type MouseEvent as ReactMouseEvent } from 'react'
import { createPortal } from 'react-dom'

export interface ContextMenuItem {
  label: string
  danger?: boolean
  onClick?: (pos: { x: number; y: number }) => void
  children?: ContextMenuItem[]
}

interface ContextMenuProps {
  items: ContextMenuItem[]
  children: ReactNode
}

function MenuList({ items, rootPos, onClose }: { items: ContextMenuItem[]; rootPos: { x: number; y: number }; onClose: () => void }) {
  const [subOpen, setSubOpen] = useState<number | null>(null)
  const [subPos, setSubPos] = useState({ x: 0, y: 0 })
  const [adjustedPos, setAdjustedPos] = useState(rootPos)
  const containerRef = useRef<HTMLDivElement>(null)
  const subRefs = useRef<Map<number, HTMLElement>>(new Map())

  useLayoutEffect(() => {
    if (!containerRef.current) return
    const rect = containerRef.current.getBoundingClientRect()
    let x = rootPos.x
    let y = rootPos.y

    if (x + rect.width > window.innerWidth - 4) x = rootPos.x - rect.width
    if (y + rect.height > window.innerHeight - 4) y = rootPos.y - rect.height
    if (x < 4) x = 4
    if (y < 4) y = 4
    setAdjustedPos({ x, y })
  }, [rootPos])

  useLayoutEffect(() => {
    if (subOpen === null) return
    const triggerEl = subRefs.current.get(subOpen)
    const subMenuEl = document.querySelector(`[data-submenu="${subOpen}"]`)
    if (!triggerEl || !subMenuEl) return
    const triggerRect = triggerEl.getBoundingClientRect()
    const subRect = subMenuEl.getBoundingClientRect()
    let sx = triggerRect.right + 4
    let sy = triggerRect.top
    if (sx + subRect.width > window.innerWidth - 4) sx = triggerRect.left - subRect.width - 4
    if (sy + subRect.height > window.innerHeight - 4) sy = window.innerHeight - subRect.height - 4
    if (sy < 4) sy = 4
    setSubPos({ x: sx, y: sy })
  }, [subOpen])

  const handleItemClick = (item: ContextMenuItem) => {
    if (item.children) return
    item.onClick?.({ x: rootPos.x, y: rootPos.y })
    onClose()
  }

  return (
    <>
      <div
        ref={containerRef}
        className="fixed z-[200] inline-flex flex-col bg-surface rounded-xl shadow-lg border border-border-light py-1 animate-scale-in"
        style={{ left: adjustedPos.x, top: adjustedPos.y }}
      >
        {items.map((item, i) => (
          <button
            key={i}
            ref={(el) => { if (el) subRefs.current.set(i, el); else subRefs.current.delete(i) }}
            onClick={() => handleItemClick(item)}
            onMouseEnter={() => { if (item.children) setSubOpen(i) }}
            className={`w-full text-center px-4 py-2 text-sm cursor-pointer hover:bg-surface-hover active:bg-surface-active transition-colors duration-100 relative ${
              item.danger ? 'text-red-500 hover:bg-red-50 dark:hover:bg-red-950' : 'text-text-primary'
            }`}
          >
            <span className="whitespace-nowrap">{item.label}</span>
            {item.children && (
              <svg className="absolute right-2 top-1/2 -translate-y-1/2 w-3 h-3 text-text-secondary shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            )}
          </button>
        ))}
      </div>
      {subOpen !== null && items[subOpen]?.children && (
        <div
          data-submenu={subOpen}
          className="fixed z-[201] inline-flex flex-col bg-surface rounded-xl shadow-lg border border-border-light py-1 animate-scale-in"
          style={{ left: subPos.x, top: subPos.y }}
          onMouseLeave={() => setSubOpen(null)}
        >
          {items[subOpen].children!.map((child, j) => (
            <button
              key={j}
              onClick={() => {
                child.onClick?.({ x: rootPos.x, y: rootPos.y })
                setSubOpen(null)
                onClose()
              }}
              className={`w-full text-center px-4 py-2 text-sm cursor-pointer hover:bg-surface-hover active:bg-surface-active transition-colors duration-100 whitespace-nowrap ${
                child.danger ? 'text-red-500 hover:bg-red-50 dark:hover:bg-red-950' : 'text-text-primary'
              }`}
            >
              {child.label}
            </button>
          ))}
        </div>
      )}
    </>
  )
}



export default function ContextMenu({ items, children }: ContextMenuProps) {
  const [open, setOpen] = useState(false)
  const [rawPos, setRawPos] = useState({ x: 0, y: 0 })
  const triggerRef = useRef<HTMLDivElement>(null)
  const menuWrapperRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: globalThis.MouseEvent) => {
      if (menuWrapperRef.current && !menuWrapperRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    const keyHandler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    document.addEventListener('keydown', keyHandler)
    return () => {
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', keyHandler)
    }
  }, [open])

  const handleContextMenu = (e: ReactMouseEvent) => {
    e.preventDefault()
    setRawPos({ x: e.clientX, y: e.clientY })
    setOpen(true)
  }

  return (
    <div ref={triggerRef} onContextMenu={handleContextMenu}>
      {children}
      {open &&
        createPortal(
          <div ref={menuWrapperRef}>
            <MenuList items={items} rootPos={rawPos} onClose={() => setOpen(false)} />
          </div>,
          document.body,
        )}
    </div>
  )
}
