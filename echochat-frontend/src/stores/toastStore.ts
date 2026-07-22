import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  type: ToastType
  message: string
  duration?: number
  exiting?: boolean
}

interface ToastState {
  toasts: Toast[]
  addToast: (toast: Omit<Toast, 'id' | 'exiting'>) => void
  removeToast: (id: string) => void
  startExit: (id: string) => void
}

let counter = 0

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  addToast: (toast) => {
    const id = `toast-${++counter}-${Date.now()}`
    const duration = toast.duration ?? 3000

    set((state) => ({
      toasts: [...state.toasts, { ...toast, id, exiting: false }],
    }))

    if (duration > 0) {
      setTimeout(() => {
        get().startExit(id)
      }, duration)
    }
  },

  startExit: (id) => {
    set((state) => ({
      toasts: state.toasts.map((t) =>
        t.id === id ? { ...t, exiting: true } : t,
      ),
    }))
    setTimeout(() => {
      get().removeToast(id)
    }, 200)
  },

  removeToast: (id) => {
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    }))
  },
}))
