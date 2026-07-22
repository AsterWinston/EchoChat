import type { Gender, UserStatus } from './api'


export interface User {
  uid: string | number
  nickname: string
  email?: string
  password?: string
  avatar: string
  signature: string
  gender: Gender
  age: number
  status: UserStatus
  lastSeen: string
  createdAt: string
  updatedAt: string
  isDeleted: number
}


export interface DeviceInfo {
  id: number
  uid: number
  deviceId: string
  platform: string
  ip: string
  loginAt: string
  lastActiveAt: string
  pushToken: string | null
}


export interface LoginRequest {
  account: string
  password: string
  deviceInfo?: {
    platform: string
    pushToken?: string
  }
}


export interface RegisterRequest {
  nickname: string
  email?: string
  password: string
}
