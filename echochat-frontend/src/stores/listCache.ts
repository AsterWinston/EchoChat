import type { FriendInfo } from '@/types/friend'
import type { GroupInfo } from '@/types/group'

export const listCache: {
  friends: FriendInfo[] | null
  ownedGroups: GroupInfo[] | null
  joinedGroups: GroupInfo[] | null
} = {
  friends: null,
  ownedGroups: null,
  joinedGroups: null,
}

export function clearListCache() {
  listCache.friends = null
  listCache.ownedGroups = null
  listCache.joinedGroups = null
}
