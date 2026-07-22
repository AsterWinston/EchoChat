import { Outlet } from 'react-router-dom'

export default function AuthLayout() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-white to-primary-100 select-none">
      <div className="w-full max-w-md px-6">
        <div className="bg-surface rounded-2xl shadow-lg shadow-primary-500/10 p-8 animate-fade-in">
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary-500 shadow-sm shadow-primary-500/30 mb-4">
              <svg
                className="w-8 h-8 text-white"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold text-text-primary">EchoChat</h1>
            <p className="text-sm text-text-secondary mt-1">
              End-to-end instant messaging
            </p>
          </div>
          <Outlet />
        </div>
        <p className="text-center text-xs text-text-secondary mt-6">
          Secure messaging, anytime, anywhere
        </p>
      </div>
    </div>
  )
}
