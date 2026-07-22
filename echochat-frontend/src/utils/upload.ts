import client from '@/api/client'
import type { Result } from '@/types/api'
import type { FileMeta } from '@/api/file'

export interface UploadResult {
  msgType: 'IMAGE' | 'VIDEO' | 'FILE' | 'VOICE'
  content: string
}

export interface UploadOptions {
  onProgress?: (pct: number) => void
  meta?: Record<string, unknown>
  signal?: AbortSignal
}

function getMsgType(mimeType: string): 'IMAGE' | 'VIDEO' | 'FILE' | 'VOICE' {
  if (mimeType.startsWith('image/')) return 'IMAGE'
  if (mimeType.startsWith('video/')) return 'VIDEO'
  if (mimeType.startsWith('audio/')) return 'VOICE'
  return 'FILE'
}

function getBucketType(mimeType: string): string {
  if (mimeType.startsWith('image/')) return 'image'
  if (mimeType.startsWith('video/')) return 'video'
  if (mimeType.startsWith('audio/')) return 'voice'
  return 'file'
}

export async function uploadFile(file: File, options?: UploadOptions): Promise<UploadResult> {
  const ext = file.name.split('.').pop() || 'bin'
  const msgType = getMsgType(file.type)
  const bucketType = getBucketType(file.type)

  const formData = new FormData()
  formData.append('file', file)

  const res = await client.post<never, { data: Result<FileMeta> }>(
    `/file/upload?ext=${ext}&type=${bucketType}`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
      signal: options?.signal,
      onUploadProgress: (e) => {
        if (e.total && options?.onProgress) {
          options.onProgress(Math.round((e.loaded / e.total) * 100))
        }
      },
    },
  )
  const meta = res.data.data

  const content = msgType === 'IMAGE'
    ? JSON.stringify([{ name: file.name, fileId: meta.fileId, size: file.size, mimeType: file.type }])
    : JSON.stringify({
        name: file.name,
        url: '',
        fileId: meta.fileId,
        size: file.size,
        mimeType: file.type,
        ...(options?.meta || {}),
      })

  return { msgType, content }
}
