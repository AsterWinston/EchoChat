import { createPortal } from 'react-dom'
import { useToastStore, type ToastType } from '@/stores/toastStore'

const iconMap: Record<ToastType, string> = {
  success: '✓',
  error: '✕',
  warning: '!',
  info: 'i',
}

const bgMap: Record<ToastType, string> = {
  success: 'bg-emerald-500',
  error: 'bg-red-500',
  warning: 'bg-amber-500',
  info: 'bg-primary-500',
}

export default function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts)
  const startExit = useToastStore((s) => s.startExit)

  if (toasts.length === 0) return null

  return createPortal(
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 pointer-events-none">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          onClick={() => startExit(toast.id)}
          role="status"
          aria-live="polite"
          className={`
            pointer-events-auto flex items-center gap-3
            px-4 py-3 rounded-xl shadow-lg
            text-white text-sm font-medium
            w-[calc(100vw-2rem)] sm:w-auto sm:min-w-[280px] max-w-sm cursor-pointer
            ${bgMap[toast.type]}
            ${toast.exiting ? 'animate-slide-out-right' : 'animate-slide-in-right'}
          `}
        >
          <span className="flex items-center justify-center w-6 h-6 rounded-full bg-white/20 text-xs font-bold shrink-0">
            {iconMap[toast.type]}
          </span>
          <span className="flex-1">{toast.message}</span>
        </div>
      ))}
    </div>,
    document.body,
  )
}

export function toast(message: string, type: ToastType = 'info', duration?: number) {
  useToastStore.getState().addToast({ type, message, duration })
}
