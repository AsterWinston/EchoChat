import { createContext, useContext, useState, type ReactNode } from 'react'

const SearchContext = createContext<string>('')

export function useSearchQuery() {
  return useContext(SearchContext)
}

export default function ChatSidebar({ children }: ChatSidebarProps) {
  const [query, setQuery] = useState('')

  return (
    <SearchContext.Provider value={query.toLowerCase()}>
      <aside className="w-80 bg-surface border-r border-border-light flex flex-col shrink-0 animate-fade-in">
        <div className="px-4 pt-4 pb-3">
          <div className="relative">
            <svg
              className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.35-4.35" />
            </svg>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search..."
              className="w-full bg-surface-active rounded-xl pl-9 pr-4 py-2 text-sm text-text-primary
                         placeholder:text-text-secondary/50
                         focus:outline-none focus:bg-surface focus:ring-2 focus:ring-primary-100
                         transition-all duration-200"
            />
          </div>
        </div>
        <div className="flex-1 flex flex-col overflow-hidden">
          {children}
        </div>
      </aside>
    </SearchContext.Provider>
  )
}

interface ChatSidebarProps {
  children: ReactNode
}
