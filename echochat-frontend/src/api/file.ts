import client from './client'
import type { Result } from '@/types/api'

export interface FileMeta {
  fileId: string
  uid: string
  bucket: string
  objectPath: string
  originalName: string
  size: number
  ext: string
  createdAt: string
}

export async function downloadFile(fileId: string): Promise<string> {
  const res = await client.get(`/file/proxy/${fileId}`, { responseType: 'blob' })
  return URL.createObjectURL(res.data as Blob)
}

export async function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await client.post<never, { data: Result<FileMeta> }>(
    `/file/upload?ext=${file.name.split('.').pop() || 'png'}&type=image`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return res.data.data.fileId
}

export async function uploadFile(file: File, bucketType: string): Promise<FileMeta> {
  const formData = new FormData()
  formData.append('file', file)
  const ext = file.name.split('.').pop() || 'bin'
  const res = await client.post<never, { data: Result<FileMeta> }>(
    `/file/upload?ext=${ext}&type=${bucketType}`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return res.data.data
}

export async function presignUploadUrl(ext: string, type: string): Promise<{ url: string; fields: Record<string, string>; fileId: string }> {
  const res = await client.post<never, { data: Result<{ url: string; fields: Record<string, string>; fileId: string }> }>('/file/presign-url', null, { params: { ext, type } })
  return res.data.data
}

export async function commitUpload(fileId: string, metadata?: Record<string, unknown>): Promise<FileMeta> {
  const res = await client.post<never, { data: Result<FileMeta> }>('/file/commit', { fileId, ...metadata })
  return res.data.data
}

export async function getDownloadUrl(fileId: string): Promise<string> {
  const res = await client.get<never, { data: Result<string> }>(`/file/download/${fileId}`)
  return res.data.data
}

export async function getFileInfo(fileId: string): Promise<FileMeta> {
  const res = await client.get<never, { data: Result<FileMeta> }>(`/file/info/${fileId}`)
  return res.data.data
}
