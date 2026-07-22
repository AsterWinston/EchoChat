import { useState, useEffect, type ImgHTMLAttributes } from 'react'
import { downloadFile } from '@/api/file'

type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl'
type AvatarStatus = 'online' | 'offline'

interface AvatarProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'size'> {
  src?: string
  size?: AvatarSize
  status?: AvatarStatus
  fallback?: string
}

const sizeStyles: Record<AvatarSize, string> = {
  xs: 'w-6 h-6 text-[10px]',
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-sm',
  lg: 'w-12 h-12 text-base',
  xl: 'w-16 h-16 text-xl',
}

const statusDotStyles: Record<AvatarSize, string> = {
  xs: 'w-2 h-2 border right-0 bottom-0',
  sm: 'w-2.5 h-2.5 border-2 right-0 bottom-0',
  md: 'w-3 h-3 border-2 right-0 bottom-0',
  lg: 'w-3.5 h-3.5 border-2 right-0 bottom-0',
  xl: 'w-4 h-4 border-[3px] right-0.5 bottom-0.5',
}

const resolvedCache = new Map<string, string>()

export default function Avatar({
  src,
  size = 'md',
  status,
  fallback,
  className = '',
  alt = '',
  ...props
}: AvatarProps) {
  const [error, setError] = useState(false)
  const [resolvedSrc, setResolvedSrc] = useState<string | null>(null)

  useEffect(() => {
    if (!src) {
      setResolvedSrc(null)
      return
    }
    if (src.startsWith('http') || src.startsWith('blob:') || src.startsWith('/')) {
      setResolvedSrc(src)
      return
    }
    if (resolvedCache.has(src)) {
      setResolvedSrc(resolvedCache.get(src)!)
      return
    }
    let cancelled = false
    downloadFile(src)
      .then((url) => {
        if (!cancelled) {
          resolvedCache.set(src, url)
          setResolvedSrc(url)
        }
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })
    return () => { cancelled = true }
  }, [src])

  const useFallback = error || !resolvedSrc

  const initials = fallback
    ? fallback
        .split(' ')
        .slice(0, 2)
        .map((w) => w[0])
        .join('')
        .toUpperCase()
    : '?'

  return (
    <div className={`relative inline-flex shrink-0 ${className}`}>
      {useFallback ? (
        <div
          className={`
            ${sizeStyles[size]} rounded-full
            bg-primary-100 text-primary-600
            flex items-center justify-center font-semibold
          `}
        >
          {initials}
        </div>
      ) : (
        <img
          src={resolvedSrc}
          alt={alt}
          onError={() => setError(true)}
          className={`${sizeStyles[size]} rounded-full object-cover`}
          {...props}
        />
      )}
      {status && (
        <span
          className={`
            absolute rounded-full ${statusDotStyles[size]}
            border-solid border-surface
            ${status === 'online' ? 'bg-green-400' : 'bg-gray-300 dark:bg-gray-600'}
          `}
        />
      )}
    </div>
  )
}
