self.addEventListener('message', (event) => {
  const { title, body, icon, url } = event.data
  if (!title) return

  self.registration.showNotification(title, {
    body: body || '',
    icon: icon || '/favicon.svg',
    badge: '/favicon.svg',
    data: { url },
    requireInteraction: false,
  })
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url || '/chat'

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      const existing = clients.find((c) => c.url.includes(self.location.origin))
      if (existing) {
        existing.focus()
        existing.postMessage({ type: 'navigate', url })
      } else {
        self.clients.openWindow(url)
      }
    }),
  )
})
