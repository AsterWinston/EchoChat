import client from './client'
import type { Result } from '@/types/api'
import type { Favorite } from '@/types/favorite'

export async function getFavorites(type?: string): Promise<Favorite[]> {
  const params = type ? { type } : {}
  const res = await client.get<never, { data: Result<Favorite[]> }>('/favorite', { params })
  return res.data.data
}

export async function addFavorite(msgId: string): Promise<void> {
  await client.post('/favorite', { msgId })
}

export async function removeFavorite(msgId: string): Promise<void> {
  await client.delete(`/favorite/${msgId}`)
}

export async function removeFavorites(msgIds: string[]): Promise<void> {
  await client.delete('/favorite/batch', { data: { ids: msgIds } })
}
