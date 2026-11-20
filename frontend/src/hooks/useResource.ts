import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api/client'

export function useResource<T>(path: string, refreshOnFocus = false) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [failure, setFailure] = useState<Error | null>(null)
  const latestRequest = useRef(0)

  const refresh = useCallback(async () => {
    const request = ++latestRequest.current
    setLoading(true)
    setFailure(null)
    try {
      const result = await api<T>(path)
      if (request === latestRequest.current) setData(result)
    } catch (cause) {
      if (request === latestRequest.current) {
        setFailure(cause instanceof Error ? cause : new Error('Не удалось загрузить данные.'))
      }
    } finally {
      if (request === latestRequest.current) setLoading(false)
    }
  }, [path])

  useEffect(() => {
    void refresh()
    return () => { latestRequest.current++ }
  }, [refresh])

  useEffect(() => {
    if (!refreshOnFocus) return
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [refresh, refreshOnFocus])

  return { data, loading, error: failure?.message ?? '', apiError: failure instanceof ApiError ? failure : null, refresh }
}
