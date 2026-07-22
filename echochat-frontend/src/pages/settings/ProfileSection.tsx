import { useState, useEffect, type FormEvent } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { updateProfile, getOwnProfile } from '@/api/user'
import { uploadAvatar, downloadFile } from '@/api/file'
import { toast } from '@/components/ui/Toast'

export default function ProfileSection() {
  const user = useAuthStore((s) => s.user)
  const setUser = useAuthStore((s) => s.setUser)

  const [nickname, setNickname] = useState(user?.nickname || '')
  const [signature, setSignature] = useState(user?.signature || '')
  const [gender, setGender] = useState(user?.gender ?? 0)
  const [age, setAge] = useState(user?.age ?? 18)
  const [saving, setSaving] = useState(false)
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (user?.avatar) {
      if (user.avatar.startsWith('http') || user.avatar.startsWith('/')) {
        setAvatarUrl(user.avatar)
        return
      }
      downloadFile(user.avatar).then(setAvatarUrl).catch(() => setAvatarUrl(null))
    }
  }, [user?.avatar])

  if (!user) return null

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const fileId = await uploadAvatar(file)
      const url = await downloadFile(fileId)
      setAvatarUrl(url)
      const updated = await updateProfile({ avatar: fileId } as Partial<import('@/types/user').User>)
      setUser(updated)
      toast('Avatar updated', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Upload failed', 'error')
    } finally {
      setUploading(false)
    }
  }

  const handleSave = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    try {
      const updated = await updateProfile({
        nickname: nickname.trim(),
        signature: signature.trim(),
        gender: gender as 0 | 1 | 2,
        age,
      })
      setUser(updated)
      toast('Profile updated', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to update', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleRefresh = async () => {
    try {
      const profile = await getOwnProfile()
      setUser(profile)
      setNickname(profile.nickname)
      setSignature(profile.signature || '')
      setGender(profile.gender)
      setAge(profile.age)
    } catch { }
  }

  return (
    <form onSubmit={handleSave} className="flex flex-col gap-5">
      <div className="bg-surface rounded-xl border border-border-light p-5">
        <h3 className="text-sm font-medium text-text-primary mb-4">Avatar</h3>
        <label className="relative group inline-block cursor-pointer">
          <input type="file" accept="image/*" className="sr-only" onChange={handleAvatarChange} />
          {avatarUrl ? (
            <img src={avatarUrl} alt="" className="w-16 h-16 rounded-2xl object-cover" />
          ) : (
            <div className="w-16 h-16 rounded-2xl bg-primary-100 flex items-center justify-center text-2xl font-semibold text-primary-600">
              {user.nickname?.charAt(0)?.toUpperCase() || '?'}
            </div>
          )}
          <div className="absolute inset-0 rounded-2xl bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
            {uploading ? (
              <svg className="animate-spin w-5 h-5 text-white" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            ) : (
              <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z" /><circle cx="12" cy="13" r="4" /></svg>
            )}
          </div>
        </label>
      </div>

      <div className="bg-surface rounded-xl border border-border-light p-5">
        <h3 className="text-sm font-medium text-text-primary mb-4">Basic Information</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Nickname</span>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="Your display name"
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Signature</span>
            <input
              type="text"
              value={signature}
              onChange={(e) => setSignature(e.target.value)}
              placeholder="A short bio"
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Gender</span>
            <select
              value={gender}
              onChange={(e) => setGender(Number(e.target.value) as 0 | 1 | 2)}
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            >
              <option value={0}>Unknown</option>
              <option value={1}>Male</option>
              <option value={2}>Female</option>
            </select>
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Age</span>
            <input
              type="number"
              value={age}
              onChange={(e) => setAge(Number(e.target.value))}
              min={1}
              max={150}
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={handleRefresh}
          className="px-5 py-2.5 text-sm text-text-secondary hover:text-text-primary hover:bg-surface-hover rounded-lg transition-colors cursor-pointer"
        >
          Reset from server
        </button>
        <button
          type="submit"
          disabled={saving || !nickname.trim()}
          className="px-5 py-2.5 bg-primary-500 text-white text-sm font-medium rounded-lg hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
        >
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </form>
  )
}
