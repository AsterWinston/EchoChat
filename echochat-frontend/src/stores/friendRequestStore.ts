import { create } from 'zustand'
import { getReceivedRequests } from '@/api/friend'

interface FriendRequestState {
  pendingCount: number
  refresh: () => Promise<void>
  decrement: () => void
}

export const useFriendRequestStore = create<FriendRequestState>((set) => ({
  pendingCount: 0,

  refresh: async () => {
    try {
      const data = await getReceivedRequests('pending')
      set({ pendingCount: data.length })
    } catch {

    }
  },

  decrement: () => {
    set((s) => ({ pendingCount: Math.max(0, s.pendingCount - 1) }))
  },
}))
