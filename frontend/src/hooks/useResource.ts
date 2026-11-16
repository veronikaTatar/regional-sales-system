import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
export function useResource<T>(path: string) {
  const [data,setData]=useState<T | null>(null)
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState('')
  const refresh=useCallback(async()=>{
    setLoading(true); setError('')
    try { setData(await api<T>(path)) } catch(cause) { setError(cause instanceof Error ? cause.message : 'Не удалось загрузить данные.') } finally { setLoading(false) }
  },[path])
  useEffect(()=>{void refresh()},[refresh])
  return { data, loading, error, refresh }
}
