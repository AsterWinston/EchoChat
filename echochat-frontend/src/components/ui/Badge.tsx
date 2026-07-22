import type { ReactNode } from 'react'

type BadgeVariant = 'dot' | 'number'

interface BadgeProps {
  count?: number
  maxCount?: number
  variant?: BadgeVariant
  className?: string
  children?: ReactNode
}



export default function Badge({
  count = 0,
  maxCount = 99,
  variant = 'number',
  className = '',
  children,
}: BadgeProps) {
  if (variant === 'dot') {
    return (
      <div className={`relative inline-flex ${className}`}>
        {children}
        <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-red-500 ring-2 ring-white" />
      </div>
    )
  }

  if (count <= 0) {
    return <>{children}</>
  }

  const display = count > maxCount ? `${maxCount}+` : String(count)

  return (
    <div className={`relative inline-flex ${className}`}>
      {children}
      <span
        className={`
          absolute -top-1.5 -right-1.5
          min-w-[18px] h-[18px] px-1
          flex items-center justify-center
          rounded-full bg-red-500
          text-[10px] font-bold text-white
          leading-none ring-2 ring-white
        `}
      >
        {display}
      </span>
    </div>
  )
}
