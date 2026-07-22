import { useState, useCallback, type FormEvent } from 'react'
import Modal from '@/components/ui/Modal'

interface Props {
  open: boolean
  onClose: () => void
  onCreate: (name: string) => Promise<void>
}

export default function CreateGroupModal({ open, onClose, onCreate }: Props) {
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleClose = useCallback(() => {
    setName('')
    setError('')
    onClose()
  }, [onClose])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Please enter a group name')
      return
    }
    setLoading(true)
    setError('')
    try {
      await onCreate(trimmed)
      setName('')
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create group')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create Group" size="sm">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input
          type="text"
          value={name}
          onChange={(e) => { setName(e.target.value); setError('') }}
          placeholder="Group name"
          autoFocus
          className="w-full px-3 py-2 text-sm bg-surface-active rounded-lg border-none outline-none focus:ring-1 focus:ring-primary-200"
        />
        {error && <p className="text-xs text-red-500">{error}</p>}
        <button
          type="submit"
          disabled={loading || !name.trim()}
          className="w-full py-2 rounded-lg bg-primary-500 text-white text-sm font-medium hover:bg-primary-400 disabled:opacity-40 transition-colors cursor-pointer"
        >
          {loading ? 'Creating...' : 'Create'}
        </button>
      </form>
    </Modal>
  )
}
