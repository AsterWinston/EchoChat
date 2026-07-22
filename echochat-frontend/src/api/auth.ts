import client from './client'
import type { Result } from '@/types/api'
import type { DeviceInfo } from '@/types/user'

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await client.put('/auth/password', { oldPassword, newPassword })
}

export async function getDevices(): Promise<DeviceInfo[]> {
  const res = await client.get<never, { data: Result<DeviceInfo[]> }>('/auth/devices')
  return res.data.data
}

export async function kickDevice(deviceId: string): Promise<void> {
  await client.delete(`/auth/devices/${deviceId}`)
}
