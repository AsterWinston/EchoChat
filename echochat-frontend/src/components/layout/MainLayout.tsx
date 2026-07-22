import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useEffect, useRef, useState, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useThemeStore } from '@/stores/themeStore'
import { useWebSocket } from '@/hooks/useWebSocket'
import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import ChatSidebar from '@/components/chat/ChatSidebar'
import ContactList from '@/components/chat/ContactList'
import GroupList from '@/components/chat/GroupList'
import ConversationList from '@/components/chat/ConversationList'
import client from '@/api/client'
import { resetClientState } from '@/utils/resetClientState'
import { useFriendRequestStore } from '@/stores/friendRequestStore'

const navItems = [
  {
    to: '/chat',
    label: 'Chats',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
      </svg>
    ),
  },
  {
    to: '/contacts',
    label: 'Contacts',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
      </svg>
    ),
  },
  {
    to: '/groups',
    label: 'Groups',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <path d="M8 14s1.5 2 4 2 4-2 4-2" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    ),
  },
  {
    to: '/moments',
    label: 'Moments',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
        <line x1="3" y1="9" x2="21" y2="9" />
        <line x1="9" y1="21" x2="9" y2="9" />
      </svg>
    ),
  },
  {
    to: '/search',
    label: 'Search',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
  },
  {
    to: '/notifications',
    label: 'Notif.',
    icon: (
      <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 01-3.46 0" />
      </svg>
    ),
  },
]

const sidebarRoutes = ['/chat', '/contacts', '/groups']



export default function MainLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const loadConversations = useChatStore((s) => s.loadConversations)
  const refreshConversations = useChatStore((s) => s.refreshConversations)
  const totalUnread = useChatStore((s) => s.conversations.reduce((sum, c) => sum + c.unreadCount, 0))
  const navigate = useNavigate()
  const location = useLocation()
  const showSidebar = sidebarRoutes.some((r) => location.pathname.startsWith(r))
  const pollRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined)

  const friendRequestCount = useFriendRequestStore((s) => s.pendingCount)
  const refreshFriendRequests = useFriendRequestStore((s) => s.refresh)
  const loadNotifications = useNotificationStore((s) => s.loadNotifications)
  const notificationUnread = useNotificationStore((s) => s.unreadCount)
  const theme = useThemeStore((s) => s.theme)
  const toggleTheme = useThemeStore((s) => s.toggle)

  const [menuOpen, setMenuOpen] = useState(false)
  const avatarRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useWebSocket()

  useEffect(() => {
    loadConversations()
    refreshFriendRequests()
    const t = setTimeout(() => {
      pollRef.current = setInterval(() => {
        const state = useChatStore.getState()
        if (!state.wsConnected) {
          state.refreshConversations()
          loadNotifications()
        }

        refreshFriendRequests()
      }, 8000)
    }, 5000)
    return () => {
      clearTimeout(t)
      clearInterval(pollRef.current)
    }
  }, [loadConversations, refreshConversations, loadNotifications, refreshFriendRequests])

  useEffect(() => {
    if (!menuOpen) return
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    const keyHandler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('mousedown', handler)
    document.addEventListener('keydown', keyHandler)
    return () => {
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', keyHandler)
    }
  }, [menuOpen])

  const handleLogout = async () => {
    setMenuOpen(false)
    try {
      await client.post('/auth/logout')
    } catch {

    }
    logout()
    resetClientState()
    navigate('/login')
  }

  const handleSelectContact = (uid: string) => navigate(`/chat/${uid}`)
  const handleSelectGroup = (gid: string) => navigate(`/chat/group/${gid}`)

  const handleNavigate = useCallback((path: string) => {
    setMenuOpen(false)
    navigate(path)
  }, [navigate])

  const getMenuPosition = () => {
    if (!avatarRef.current) return { bottom: 16, left: 0 }
    const rect = avatarRef.current.getBoundingClientRect()
    return {
      bottom: window.innerHeight - rect.top + 8,
      left: rect.right + 8,
    }
  }

  return (
    <div className="flex h-screen bg-bg-primary overflow-hidden">
      {}
      <aside className="w-18 flex flex-col items-center bg-surface border-r border-border-light py-4 shrink-0 select-none">
        <div className="flex flex-col items-center gap-1">
          <NavLink
            to="/chat"
            className="w-12 h-12 flex items-center justify-center rounded-xl mb-2 shrink-0"
          >
            <div className="w-9 h-9 rounded-xl bg-primary-500 shadow-sm shadow-primary-500/30 flex items-center justify-center">
              <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
              </svg>
            </div>
          </NavLink>

          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `w-12 h-12 flex flex-col items-center justify-center rounded-xl gap-0.5 transition-colors duration-200 shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                  isActive
                    ? 'bg-primary-50 text-primary-600'
                     : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary'
                }`
              }
            >
              {item.to === '/chat' ? (
                <Badge count={totalUnread} variant="number">
                  {item.icon}
                </Badge>
              ) : item.to === '/contacts' ? (
                <Badge count={friendRequestCount} variant="number">
                  {item.icon}
                </Badge>
              ) : item.to === '/notifications' ? (
                <Badge count={notificationUnread} variant="number">
                  {item.icon}
                </Badge>
              ) : (
                item.icon
              )}
              <span className="text-[10px] leading-none">{item.label}</span>
            </NavLink>
          ))}
        </div>

        <div className="flex-1" />

        <div ref={avatarRef} className="relative shrink-0">
          <button
            onClick={() => setMenuOpen((v) => !v)}
            className="cursor-pointer rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Account menu"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            title="Menu"
          >
            <Avatar
              src={user?.avatar}
              fallback={user?.nickname || '?'}
              size="sm"
              status={user?.status === 1 ? 'online' : 'offline'}
            />
          </button>
        </div>
      </aside>

      {}
      {showSidebar && (
        <ChatSidebar>
          {location.pathname.startsWith('/contacts') && (
            <ContactList onSelect={handleSelectContact} />
          )}
          {location.pathname.startsWith('/groups') && (
            <GroupList onSelect={handleSelectGroup} />
          )}
          {location.pathname.startsWith('/chat') && (
            <ConversationList />
          )}
        </ChatSidebar>
      )}

      {}
      <main className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
          <Outlet />
        </div>
      </main>

      {}
      {menuOpen &&
        createPortal(
          <div
            ref={menuRef}
            className="fixed z-[300] w-48 bg-surface border border-border-light rounded-xl shadow-lg py-1 animate-scale-in"
            style={getMenuPosition()}
          >
            <button
              onClick={() => handleNavigate('/profile')}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text-primary hover:bg-surface-hover transition-colors cursor-pointer text-left"
            >
              <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
              Profile
            </button>
            <button
              onClick={() => handleNavigate('/favorites')}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text-primary hover:bg-surface-hover transition-colors cursor-pointer text-left"
            >
              <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
              </svg>
              Favorites
            </button>
            <button
              onClick={() => handleNavigate('/settings')}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text-primary hover:bg-surface-hover transition-colors cursor-pointer text-left"
            >
              <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="3" />
                <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
              </svg>
              Settings
            </button>

            <div className="border-t border-border-light my-1" />

            <button
              onClick={() => { toggleTheme(); setMenuOpen(false) }}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text-primary hover:bg-surface-hover transition-colors cursor-pointer text-left"
            >
              {theme === 'dark' ? (
                <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="5" />
                  <line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
                </svg>
              ) : (
                <svg className="w-4 h-4 text-text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                </svg>
              )}
              <span className="flex-1">{theme === 'dark' ? 'Light mode' : 'Dark mode'}</span>
              <span className={`w-4 h-4 rounded-full border-2 flex items-center justify-center ${theme === 'dark' ? 'bg-primary-500 border-primary-500' : 'border-text-secondary'}`}>
                {theme === 'dark' && <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>}
              </span>
            </button>

            <div className="border-t border-border-light my-1" />

            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-950 transition-colors cursor-pointer text-left"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
              Logout
            </button>
          </div>,
          document.body,
        )}
    </div>
  )
}
