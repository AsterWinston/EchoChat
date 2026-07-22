import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/authStore'
import type { Result } from '@/types/api'

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else if (token) {
      resolve(token)
    }
  })
  failedQueue = []
}

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('echochat-auth')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed.state?.accessToken ?? null
  } catch {
    return null
  }
}

function getRefreshToken(): string | null {
  try {
    const raw = localStorage.getItem('echochat-auth')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed.state?.refreshToken ?? null
  } catch {
    return null
  }
}

function setTokens(accessToken: string, refreshToken: string) {
  
  
  
  
  
  useAuthStore.getState().setTokens(accessToken, refreshToken)
}

function clearAuth() {
  localStorage.removeItem('echochat-auth')
  useAuthStore.getState().logout()
}

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response
    const result = response.data as Result<unknown>
    if (result.code !== 200) {
      return Promise.reject(new Error(result.message || 'Request failed'))
    }
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean; _retryCount?: number }

    
    if (error.response?.status === 429) {
      const retryCount = originalRequest._retryCount ?? 0
      if (retryCount < 3) {
        originalRequest._retryCount = retryCount + 1
        await new Promise((r) => setTimeout(r, 1000 * (retryCount + 1)))
        return client(originalRequest)
      }
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`
              }
              resolve(client(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        clearAuth()
        return Promise.reject(error)
      }

      try {
        const res = await axios.post('/api/auth/refresh', {
          refreshToken,
        })
        const data = res.data as Result<{ accessToken: string; refreshToken: string }>
        const newAccessToken = data.data.accessToken
        const newRefreshToken = data.data.refreshToken

        setTokens(newAccessToken, newRefreshToken)

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        }

        processQueue(null, newAccessToken)

        return client(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError, null)
        clearAuth()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response?.data) {
      const body = error.response.data as Result<unknown>
      if (body && typeof body === 'object' && 'message' in body && body.message) {
        return Promise.reject(new Error(body.message as string))
      }
    }

    return Promise.reject(error)
  },
)

export default client
