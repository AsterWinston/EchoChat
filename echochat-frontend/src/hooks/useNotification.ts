import { useState, useCallback, useEffect } from 'react'
import { initNotifications, setDnd as saveDnd, isDnd, requestPermission, getPermission } from '@/utils/notify'

export function useNotification() {
  const [permission, setPermission] = useState<NotificationPermission>(getPermission)
  const [dnd, setDnd] = useState(isDnd)

  useEffect(() => {
    initNotifications()
  }, [])

  const handleRequestPermission = useCallback(async () => {
    const granted = await requestPermission()
    setPermission(granted ? 'granted' : 'denied')
    return granted
  }, [])

  const handleSetDnd = useCallback((on: boolean) => {
    saveDnd(on)
    setDnd(on)
  }, [])

  return {
    permission,
    dnd,
    setDnd: handleSetDnd,
    requestPermission: handleRequestPermission,
  }
}
