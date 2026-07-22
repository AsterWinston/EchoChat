import { useNotification } from '@/hooks/useNotification'

export default function NotificationSettingsSection() {
  const { dnd, setDnd, permission, requestPermission } = useNotification()

  return (
    <div className="bg-surface rounded-xl border border-border-light p-5">
      <h3 className="text-sm font-medium text-text-primary mb-4">Notifications</h3>

      <div className="space-y-4">
        <div className="flex items-center justify-between py-1">
          <div>
            <span className="text-sm text-text-primary">Desktop Notifications</span>
            <p className="text-xs text-text-secondary mt-0.5">
              {permission === 'granted'
                ? 'Notifications are enabled'
                : permission === 'denied'
                  ? 'Notifications are blocked in browser settings'
                  : 'Click to enable desktop notifications'}
            </p>
          </div>
          {permission !== 'granted' ? (
            <button
              onClick={requestPermission}
              className="px-4 py-1.5 text-xs font-medium bg-primary-500 text-white rounded-lg hover:bg-primary-400 transition-colors cursor-pointer"
            >
              Enable
            </button>
          ) : (
            <span className="text-xs text-emerald-500 font-medium">Enabled</span>
          )}
        </div>

        <div className="flex items-center justify-between py-1 border-t border-border-light">
          <div>
            <span className="text-sm text-text-primary">Do Not Disturb</span>
            <p className="text-xs text-text-secondary mt-0.5">Mute all desktop notifications</p>
          </div>
          <button
            onClick={() => setDnd(!dnd)}
            className={`relative w-10 h-6 rounded-full transition-colors cursor-pointer ${dnd ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600'}`}
          >
            <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${dnd ? 'translate-x-4' : ''}`} />
          </button>
        </div>
      </div>
    </div>
  )
}
