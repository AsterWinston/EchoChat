import { useState, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface TooltipProps {
  content: string
  children: ReactNode
}

export default function Tooltip({ content, children }: TooltipProps) {
  const [show, setShow] = useState(false)
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const targetRef = useRef<HTMLDivElement>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const handleMouseEnter = () => {
    timerRef.current = setTimeout(() => {
      if (targetRef.current) {
        const rect = targetRef.current.getBoundingClientRect()
        setPos({
          x: rect.left + rect.width / 2,
          y: rect.top - 8,
        })
        setShow(true)
      }
    }, 400)
  }

  const handleMouseLeave = () => {
    if (timerRef.current !== undefined) clearTimeout(timerRef.current)
    setShow(false)
  }

  return (
    <div
      ref={targetRef}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className="inline-flex"
    >
      {children}
      {show && createPortal(
        <div
          className="fixed z-[250] px-2.5 py-1.5 bg-gray-900 text-white text-xs rounded-lg shadow-lg pointer-events-none animate-fade-in whitespace-nowrap"
          style={{
            left: pos.x,
            top: pos.y,
            transform: 'translate(-50%, -100%)',
          }}
        >
          {content}
        </div>,
        document.body,
      )}
    </div>
  )
}
