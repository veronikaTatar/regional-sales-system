interface Csrf { token: string; headerName: string }
let csrf: Csrf | null = null
export function resetCsrf() { csrf = null }
export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: Record<string, string>
  replacementToken?: string
  constructor(status: number, message: string, code = 'REQUEST_FAILED', fieldErrors: Record<string, string> = {},
    replacementToken?: string) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.replacementToken = replacementToken
  }
}
async function checked(response: Response): Promise<Response> {
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'))
    throw new ApiError(response.status, data.message ?? 'Не удалось выполнить запрос. Попробуйте ещё раз.',
      data.code, data.fieldErrors ?? {}, typeof data.replacementToken === 'string' ? data.replacementToken : undefined)
  }
  return response
}
async function getCsrf(): Promise<Csrf> {
  if (!csrf) {
    const response = await checked(await fetch('/api/auth/csrf', { credentials: 'include' }))
    csrf = await response.json() as Csrf
  }
  return csrf
}
async function request<T>(path: string, options: RequestInit, read: (response: Response) => T | Promise<T>): Promise<T> {
  try {
    const headers = new Headers(options.headers)
    if (typeof options.body === 'string' && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
      const token = await getCsrf()
      headers.set(token.headerName, token.token)
    }
    const send = async () => read(await checked(await fetch(`/api${path}`, { ...options, headers, credentials: 'include' })))
    try {
      return await send()
    } catch (error) {
      // A CSRF rejection happens before the controller executes, so one replay is safe.
      // Do not replay ordinary authorization failures or uncertain network failures.
      if (!(error instanceof ApiError) || error.status !== 403 || error.code !== 'CSRF_INVALID') throw error
      resetCsrf()
      const fresh = await getCsrf()
      headers.set(fresh.headerName, fresh.token)
      return await send()
    }
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(0, 'Сервер недоступен. Проверьте подключение и повторите попытку.', 'NETWORK_ERROR')
  }
}

export function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  return request(path, options, response => response.status === 204 ? undefined as T : response.json() as Promise<T>)
}

export function apiBlob(path: string): Promise<Blob> {
  return request(path, {}, response => response.blob())
}
