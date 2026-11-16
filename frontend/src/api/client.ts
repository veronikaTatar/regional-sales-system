interface Csrf { token: string; headerName: string }
let csrf: Csrf | null = null
export function resetCsrf() { csrf = null }
export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: Record<string, string>
  constructor(status: number, message: string, code = 'REQUEST_FAILED', fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}
async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'))
    throw new ApiError(response.status, data.message ?? 'Не удалось выполнить запрос. Попробуйте ещё раз.', data.code, data.fieldErrors)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
async function getCsrf(): Promise<Csrf> {
  if (!csrf) csrf = await readResponse<Csrf>(await fetch('/api/auth/csrf', { credentials: 'include' }))
  return csrf
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    const headers = new Headers(options.headers)
    if (options.body) headers.set('Content-Type', 'application/json')
    if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
      const token = await getCsrf()
      headers.set(token.headerName, token.token)
    }
    const request = () => fetch(`/api${path}`, { ...options, headers, credentials: 'include' })
    try {
      return await readResponse<T>(await request())
    } catch (error) {
      // A CSRF rejection happens before the controller executes, so one replay is safe.
      // Do not replay ordinary authorization failures or uncertain network failures.
      if (!(error instanceof ApiError) || error.status !== 403 || error.code !== 'CSRF_INVALID') throw error
      resetCsrf()
      const fresh = await getCsrf()
      headers.set(fresh.headerName, fresh.token)
      return await readResponse<T>(await request())
    }
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(0, 'Сервер недоступен. Проверьте подключение и повторите попытку.', 'NETWORK_ERROR')
  }
}
