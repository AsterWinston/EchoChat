import { useState, type FormEvent } from 'react'
import { changePassword } from '@/api/auth'
import { toast } from '@/components/ui/Toast'

export default function PasswordSection() {
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (newPassword !== confirmPassword) {
      toast('Passwords do not match', 'error')
      return
    }
    if (newPassword.length < 6) {
      toast('Password must be at least 6 characters', 'error')
      return
    }
    setLoading(true)
    try {
      await changePassword(oldPassword, newPassword)
      setOldPassword('')
      setNewPassword('')
      setConfirmPassword('')
      toast('Password changed', 'success')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Failed to change password', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-surface rounded-xl border border-border-light p-5">
      <h3 className="text-sm font-medium text-text-primary mb-4">Change Password</h3>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-4">
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Current Password</span>
            <input
              type="password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
              placeholder="••••••••"
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">New Password</span>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Min 6 characters"
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-text-secondary">Confirm New Password</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Re-enter new password"
              className="px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
            />
          </label>
        </div>
        <div className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => { setOldPassword(''); setNewPassword(''); setConfirmPassword('') }}
            className="px-5 py-2.5 text-sm text-text-secondary hover:text-text-primary hover:bg-surface-hover rounded-lg transition-colors cursor-pointer"
          >
            Clear
          </button>
          <button
            type="submit"
            disabled={loading || !oldPassword || !newPassword || !confirmPassword}
            className="px-5 py-2.5 bg-primary-500 text-white text-sm font-medium rounded-lg hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
          >
            {loading ? 'Changing...' : 'Change Password'}
          </button>
        </div>
      </form>
    </div>
  )
}
