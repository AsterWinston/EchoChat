import { useState, useEffect, useCallback } from 'react'
import { getDevices, kickDevice } from '@/api/auth'
import type { DeviceInfo } from '@/types/user'
import { toast } from '@/components/ui/Toast'

function getDeviceLabel(platform: string): { label: string; color: string } {
  switch (platform) {
    case 'windows': return { label: 'Windows', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300' }
    case 'macos': return { label: 'macOS', color: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' }
    case 'linux': return { label: 'Linux', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300' }
    case 'android': return { label: 'Android', color: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300' }
    case 'ios': return { label: 'iOS', color: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' }
    default: return { label: platform || 'Web', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300' }
  }
}

export default function DeviceSection() {
  const [devices, setDevices] = useState<DeviceInfo[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setDevices(await getDevices())
    } catch {

    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const handleKick = async (deviceId: string) => {
    try {
      await kickDevice(deviceId)
      setDevices((prev) => prev.filter((d) => d.deviceId !== deviceId))
      toast('Device logged out', 'success')
    } catch {
      toast('Failed to kick device', 'error')
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-12">
        <svg className="animate-spin h-5 w-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    )
  }

  if (devices.length === 0) {
    return (
      <div className="bg-surface rounded-xl border border-border-light p-5">
        <h3 className="text-sm font-medium text-text-primary mb-4">Active Devices</h3>
        <p className="text-sm text-text-secondary text-center py-8">No device data available</p>
      </div>
    )
  }

  return (
    <div className="bg-surface rounded-xl border border-border-light overflow-hidden">
      <div className="px-5 py-4 border-b border-border-light">
        <h3 className="text-sm font-medium text-text-primary">Active Devices</h3>
      </div>
      <div className="divide-y divide-border-light">
        {devices.map((d) => {
          const info = getDeviceLabel(d.platform)
          return (
            <div key={d.deviceId} className="flex items-center gap-4 px-5 py-3 hover:bg-surface-hover transition-colors">
              <span className={`shrink-0 text-[10px] font-medium px-2 py-0.5 rounded ${info.color}`}>
                {info.label}
              </span>
              <div className="flex-1 min-w-0 flex items-center gap-4">
                {d.ip && <span className="text-xs text-text-secondary font-mono shrink-0">{d.ip}</span>}
                <span className="text-xs text-text-secondary truncate">
                  Last active: {new Date(d.loginAt).toLocaleString()}
                </span>
              </div>
              <button
                onClick={() => handleKick(d.deviceId)}
                className="text-xs text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950 rounded px-3 py-1 transition-colors cursor-pointer shrink-0"
              >
                Kick
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}
