let _swReg: ServiceWorkerRegistration | null = null
let _swReady = false
let _dnd = false

export function initNotifications() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/service-worker.js')
      .then((reg) => {
        _swReg = reg
        return navigator.serviceWorker.ready
      })
      .then(() => { _swReady = true })
      .catch((e) => { console.error('Service worker registration failed:', e) })
  }
  _dnd = localStorage.getItem('echochat-dnd') === 'true'
}

export function setDnd(on: boolean) {
  _dnd = on
  try {
    localStorage.setItem('echochat-dnd', String(on))
  } catch {  }
}

export function isDnd(): boolean {
  return _dnd
}

export async function requestPermission(): Promise<boolean> {
  if (!('Notification' in window)) return false
  const result = await Notification.requestPermission()
  return result === 'granted'
}

export function getPermission(): NotificationPermission {
  return 'Notification' in window ? Notification.permission : 'denied'
}

export function notify(title: string, body: string, url?: string) {
  if (_dnd || document.visibilityState === 'visible') return
  if (getPermission() !== 'granted') return

  playSound()

  if (_swReady && _swReg?.active) {
    _swReg.active.postMessage({ title, body, url })
  } else {
    new Notification(title, {
      body: body || '',
      icon: '/favicon.svg',
    })
  }
}

function playSound() {
  try {
    const ctx = new AudioContext()
    const now = ctx.currentTime

    const osc1 = ctx.createOscillator()
    const gain1 = ctx.createGain()
    osc1.type = 'sine'
    osc1.frequency.setValueAtTime(840, now)
    gain1.gain.setValueAtTime(0.25, now)
    gain1.gain.linearRampToValueAtTime(0.01, now + 0.18)
    osc1.connect(gain1)
    gain1.connect(ctx.destination)
    osc1.start(now)
    osc1.stop(now + 0.2)

    const osc2 = ctx.createOscillator()
    const gain2 = ctx.createGain()
    osc2.type = 'sine'
    osc2.frequency.setValueAtTime(640, now + 0.16)
    gain2.gain.setValueAtTime(0.01, now + 0.16)
    gain2.gain.linearRampToValueAtTime(0.25, now + 0.2)
    gain2.gain.linearRampToValueAtTime(0.01, now + 0.45)
    osc2.connect(gain2)
    gain2.connect(ctx.destination)
    osc2.start(now + 0.16)
    osc2.stop(now + 0.5)
  } catch (e) { console.error('Audio play failed:', e) }
}
