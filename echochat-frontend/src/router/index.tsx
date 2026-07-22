import { createBrowserRouter, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { AuthGuard, GuestGuard } from './guards'
import MainLayout from '@/components/layout/MainLayout'
import AuthLayout from '@/components/layout/AuthLayout'
import LoginPage from '@/pages/auth/LoginPage'
import RegisterPage from '@/pages/auth/RegisterPage'
import ChatPage from '@/pages/chat/ChatPage'

const MomentsPage = lazy(() => import('@/pages/moments/MomentsPage'))
const SearchPage = lazy(() => import('@/pages/search/SearchPage'))
const SettingsPage = lazy(() => import('@/pages/settings/SettingsPage'))
const ProfilePage = lazy(() => import('@/pages/profile/ProfilePage'))
const GroupProfilePage = lazy(() => import('@/pages/profile/GroupProfilePage'))
const UserMomentsPage = lazy(() => import('@/pages/profile/UserMomentsPage'))
const NotificationPage = lazy(() => import('@/pages/notifications/NotificationPage'))
const FavoritesPage = lazy(() => import('@/pages/favorites/FavoritesPage'))

function Lazy({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={
      <div className="flex-1 flex items-center justify-center">
        <svg className="animate-spin h-6 w-6 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    }>
      {children}
    </Suspense>
  )
}



export const router = createBrowserRouter([
  {
    element: <GuestGuard />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
    ],
  },
  {
    element: <AuthGuard />,
    children: [
      {
        element: <MainLayout />,
        children: [
          { index: true, element: <Navigate to="/chat" replace /> },
          { path: '/chat', element: <ChatPage /> },
          { path: '/chat/group/:gid', element: <ChatPage /> },
          { path: '/chat/:uid', element: <ChatPage /> },
          { path: '/contacts', element: <ChatPage /> },
          { path: '/groups', element: <ChatPage /> },
          { path: '/moments', element: <Lazy><MomentsPage /></Lazy> },
          { path: '/search', element: <Lazy><SearchPage /></Lazy> },
          { path: '/settings', element: <Lazy><SettingsPage /></Lazy> },
          { path: '/profile', element: <Lazy><ProfilePage /></Lazy> },
          { path: '/profile/:uid', element: <Lazy><ProfilePage /></Lazy> },
          { path: '/group/:gid/profile', element: <Lazy><GroupProfilePage /></Lazy> },
          { path: '/moments/user/:uid', element: <Lazy><UserMomentsPage /></Lazy> },
          { path: '/notifications', element: <Lazy><NotificationPage /></Lazy> },
          { path: '/favorites', element: <Lazy><FavoritesPage /></Lazy> },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/chat" replace />,
  },
])
