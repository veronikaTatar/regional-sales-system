import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiError, resetCsrf } from '../../api/client'
import type { RegisterRequest, User } from '../../types'

interface AuthState {
  user: User | null
  loading: boolean
  startupError: string | null
  refresh: () => Promise<void>
  login: (login: string, password: string) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}
const AuthContext = createContext<AuthState | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [startupError, setStartupError] = useState<string | null>(null)
  const refresh = useCallback(async () => {
    setLoading(true)
    setStartupError(null)
    try { setUser(await api<User>('/auth/me')) }
    catch (error) {
      setUser(null)
      if (!(error instanceof ApiError) || error.status !== 401) setStartupError(error instanceof Error ? error.message : 'Не удалось проверить сессию.')
    } finally { setLoading(false) }
  }, [])
  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => {
    const expire = () => { setUser(null); resetCsrf() }
    window.addEventListener('session-expired', expire)
    return () => window.removeEventListener('session-expired', expire)
  }, [])
  const login = async (login: string, password: string) => {
    const account = await api<User>('/auth/login', { method: 'POST', body: JSON.stringify({ login, password }) })
    resetCsrf(); setUser(account); setStartupError(null)
  }
  const register = async (data: RegisterRequest) => {
    const account = await api<User>('/auth/register', { method: 'POST', body: JSON.stringify(data) })
    resetCsrf(); setUser(account); setStartupError(null)
  }
  const logout = async () => {
    await api<void>('/auth/logout', { method: 'POST' })
    resetCsrf(); setUser(null)
  }
  return <AuthContext.Provider value={{ user, loading, startupError, refresh, login, register, logout }}>{children}</AuthContext.Provider>
}
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider is required')
  return value
}
