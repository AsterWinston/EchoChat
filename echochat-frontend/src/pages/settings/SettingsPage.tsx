import { useState } from 'react'
import Tabs from '@/components/ui/Tabs'
import ProfileSection from '@/pages/settings/ProfileSection'
import PasswordSection from '@/pages/settings/PasswordSection'
import DeviceSection from '@/pages/settings/DeviceSection'
import BlacklistSection from '@/pages/settings/BlacklistSection'
import NotificationSettingsSection from '@/pages/settings/NotificationSettingsSection'

const tabs = [
  { key: 'profile', label: 'Profile' },
  { key: 'password', label: 'Password' },
  { key: 'notifications', label: 'Notifications' },
  { key: 'devices', label: 'Devices' },
  { key: 'blacklist', label: 'Blocked' },
]



export default function SettingsPage() {
  const [tab, setTab] = useState('profile')

  return (
    <div className="flex-1 flex flex-col min-h-0 animate-fade-in">
      <div className="px-6 py-4 bg-surface border-b border-border-light shrink-0">
        <h2 className="text-base font-semibold text-text-primary mb-3">Settings</h2>
        <Tabs items={tabs} activeKey={tab} onChange={setTab} />
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="px-6 py-6">
          {tab === 'profile' && <ProfileSection />}
          {tab === 'password' && <PasswordSection />}
          {tab === 'notifications' && <NotificationSettingsSection />}
          {tab === 'devices' && <DeviceSection />}
          {tab === 'blacklist' && <BlacklistSection />}
        </div>
      </div>
    </div>
  )
}
