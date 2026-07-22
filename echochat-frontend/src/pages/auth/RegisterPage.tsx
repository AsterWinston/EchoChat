import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import client from '@/api/client'
import type { Result, TokenResponse } from '@/types/api'
import type { User } from '@/types/user'
import { getDeviceId, getPlatform } from '@/utils/deviceId'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'



export default function RegisterPage() {
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const login = useAuthStore((s) => s.login)
  const navigate = useNavigate()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')

    if (!nickname.trim()) {
      setError('Please enter a nickname')
      return
    }
    if (!email.trim()) {
      setError('Please enter an email')
      return
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters')
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setLoading(true)
    try {
      const res = await client.post<never, { data: Result<TokenResponse> }>(
        '/auth/register',
        {
          nickname: nickname.trim(),
          email: email.trim(),
          password,
          deviceId: getDeviceId(),
          platform: getPlatform(),
        },
      )
      const { accessToken, refreshToken } = res.data.data

      const profileRes = await client.get<never, { data: Result<User> }>(
        '/user/profile',
        { headers: { Authorization: `Bearer ${accessToken}` } },
      )
      const user = profileRes.data.data

      login(accessToken, refreshToken, user)
      navigate('/chat', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Input
        label="Nickname"
        placeholder="Your display name"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        prefixIcon={
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
            <circle cx="12" cy="7" r="4" />
          </svg>
        }
      />
      <Input
        label="Email"
        type="email"
        placeholder="your@email.com"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        prefixIcon={
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="4" width="20" height="16" rx="2" />
            <path d="m22 7-8.97 5.7a1.94 1.94 0 01-2.06 0L2 7" />
          </svg>
        }
      />
      <Input
        label="Password"
        type="password"
        placeholder="At least 6 characters"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        prefixIcon={
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0110 0v4" />
          </svg>
        }
      />
      <Input
        label="Confirm Password"
        type="password"
        placeholder="Re-enter password"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
        prefixIcon={
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0110 0v4" />
          </svg>
        }
      />
      {error && (
        <div className="text-sm text-red-500 bg-red-50 rounded-xl px-4 py-2.5 animate-fade-in">
          {error}
        </div>
      )}
      <Button type="submit" loading={loading} size="lg" className="w-full mt-2">
        Create Account
      </Button>
      <p className="text-center text-sm text-text-secondary">
        Already have an account?{' '}
        <Link
          to="/login"
          className="text-primary-500 hover:text-primary-400 font-medium"
        >
          Log In
        </Link>
      </p>
    </form>
  )
}
