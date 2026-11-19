import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'

export function useResource<T>(path: string) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const latestRequest = useRef(0)

  const refresh = useCallback(async () => {
    const request = ++latestRequest.current
    setLoading(true)
    setError('')
    try {
      const result = await api<T>(path)
      if (request === latestRequest.current) setData(result)
    } catch (cause) {
      if (request === latestRequest.current) {
        setError(cause instanceof Error ? cause.message : 'Не удалось загрузить данные.')
      }
    } finally {
      if (request === latestRequest.current) setLoading(false)
    }
  }, [path])

  useEffect(() => {
    void refresh()
    return () => { latestRequest.current++ }
  }, [refresh])

  return { data, loading, error, refresh }
}
