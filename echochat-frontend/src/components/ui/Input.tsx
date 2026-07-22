import { type InputHTMLAttributes, type ReactNode, forwardRef } from 'react'

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'> {
  label?: string
  error?: string
  prefixIcon?: ReactNode
  suffixIcon?: ReactNode
}



const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, prefixIcon, suffixIcon, className = '', ...props }, ref) => {
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label className="text-sm font-medium text-text-primary">
            {label}
          </label>
        )}
        <div className="relative">
          {prefixIcon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary">
              {prefixIcon}
            </div>
          )}
          <input
            ref={ref}
            className={`
              w-full bg-surface border-2 border-border-light rounded-xl
              px-4 py-2.5 text-sm text-text-primary
              placeholder:text-text-secondary/60
              focus:outline-none focus:border-primary-300 focus:ring-2 focus:ring-primary-100
              disabled:bg-surface-active dark:disabled:bg-gray-800 disabled:opacity-60
              ${prefixIcon ? 'pl-10' : ''}
              ${suffixIcon ? 'pr-10' : ''}
              ${error ? 'border-red-400 focus:border-red-400 focus:ring-red-100' : ''}
              ${className}
            `}
            {...props}
          />
          {suffixIcon && (
            <div className="absolute right-3 top-1/2 -translate-y-1/2 text-text-secondary">
              {suffixIcon}
            </div>
          )}
        </div>
        {error && (
          <span className="text-xs text-red-500 animate-fade-in">{error}</span>
        )}
      </div>
    )
  },
)

Input.displayName = 'Input'

export default Input
