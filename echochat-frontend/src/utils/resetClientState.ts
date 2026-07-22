import { useChatStore } from '@/stores/chatStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useFriendRequestStore } from '@/stores/friendRequestStore'
import { clearListCache } from '@/stores/listCache'

export function resetClientState() {
  useChatStore.getState().reset()
  useNotificationStore.getState().reset()
  useFriendRequestStore.setState({ pendingCount: 0 })
  clearListCache()
}
