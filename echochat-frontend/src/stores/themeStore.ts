import { create } from 'zustand'

type Theme = 'light' | 'dark'

interface ThemeState {
  theme: Theme
  toggle: () => void
  setTheme: (theme: Theme) => void
}

function getInitialTheme(): Theme {
  try {
    const stored = localStorage.getItem('echochat-theme')
    if (stored === 'dark' || stored === 'light') return stored
  } catch {  }
  try {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark'
    }
  } catch {  }
  return 'light'
}

function applyTheme(theme: Theme) {
  const el = document.documentElement
  if (theme === 'dark') {
    el.classList.add('dark')
  } else {
    el.classList.remove('dark')
  }
  try {
    localStorage.setItem('echochat-theme', theme)
  } catch {  }
}

applyTheme(getInitialTheme())

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: getInitialTheme(),

  toggle: () => {
    const next: Theme = get().theme === 'dark' ? 'light' : 'dark'
    applyTheme(next)
    set({ theme: next })
  },

  setTheme: (theme) => {
    applyTheme(theme)
    set({ theme })
  },
}))
