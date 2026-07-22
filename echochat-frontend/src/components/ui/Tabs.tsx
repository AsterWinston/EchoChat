import type { ReactNode } from 'react'

export interface TabItem {
  key: string
  label: string
  badge?: string | number
}

interface TabsProps {
  items: TabItem[]
  activeKey: string
  onChange: (key: string) => void
  className?: string
  children?: ReactNode
}

export default function Tabs({ items, activeKey, onChange, className = '', children }: TabsProps) {
  return (
    <div className={className}>
      <div className="flex items-center gap-0 border-b border-border-light">
        {items.map((item) => (
          <button
            key={item.key}
            onClick={() => onChange(item.key)}
            className={`px-4 py-2.5 text-sm font-medium transition-colors duration-200 cursor-pointer
              ${activeKey === item.key
                ? 'text-primary-600 border-b-2 border-primary-500 bg-primary-50/30 -mb-px'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover border-b-2 border-transparent'
              }`}
          >
            {item.label}
            {item.badge !== undefined && (
              <span className="ml-1.5 text-[11px] text-text-secondary/70">({item.badge})</span>
            )}
          </button>
        ))}
      </div>
      {children}
    </div>
  )
}
