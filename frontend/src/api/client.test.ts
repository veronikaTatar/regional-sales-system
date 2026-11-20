import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, apiBlob, ApiError, resetCsrf } from './client'
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {status,headers:{'Content-Type':'application/json'}})
beforeEach(resetCsrf)
afterEach(()=>vi.unstubAllGlobals())
describe('session API client',()=>{
  it('refreshes a stale CSRF token and replays a rejected mutation exactly once',async()=>{
    let csrfReads=0
    const tokens:string[]=[]
    const fetch=vi.fn(async(url:RequestInfo|URL,options?:RequestInit)=>{
      if(url==='/api/auth/csrf') return json({token:++csrfReads===1?'stale':'fresh',headerName:'X-CSRF-TOKEN'})
      tokens.push(new Headers(options?.headers).get('X-CSRF-TOKEN')??'')
      return tokens.length===1 ? json({code:'CSRF_INVALID',message:'Недействительный токен'},403) : json({saved:true})
    })
    vi.stubGlobal('fetch',fetch)
    expect(await api('/example',{method:'POST',body:'{}'})).toEqual({saved:true})
    expect(tokens).toEqual(['stale','fresh'])
    expect(csrfReads).toBe(2)
    expect(fetch).toHaveBeenCalledTimes(4)
  })
  it('does not retry a role authorization rejection',async()=>{
    const fetch=vi.fn(async(url:RequestInfo|URL)=>url==='/api/auth/csrf'?json({token:'valid',headerName:'X-CSRF-TOKEN'}):json({code:'FORBIDDEN',message:'Доступ запрещён'},403))
    vi.stubGlobal('fetch',fetch)
    await expect(api('/users/1',{method:'PATCH',body:'{}'})).rejects.toMatchObject({status:403,code:'FORBIDDEN'})
    expect(fetch).toHaveBeenCalledTimes(2)
  })
  it('stops after one CSRF retry if the second token also fails',async()=>{
    const fetch=vi.fn(async(url:RequestInfo|URL)=>url==='/api/auth/csrf'?json({token:'token',headerName:'X-CSRF-TOKEN'}):json({code:'CSRF_INVALID',message:'Ошибка CSRF'},403))
    vi.stubGlobal('fetch',fetch)
    await expect(api('/example',{method:'POST'})).rejects.toBeInstanceOf(ApiError)
    expect(fetch).toHaveBeenCalledTimes(4)
  })
  it('lets the browser set the multipart boundary when uploading an Excel file', async () => {
    const form = new FormData()
    form.set('file', new File(['xlsx bytes'], 'nomenklatura.xlsx'))
    const fetch = vi.fn(async (url: RequestInfo | URL, options?: RequestInit) => {
      if (url === '/api/auth/csrf') return json({ token: 'valid', headerName: 'X-CSRF-TOKEN' })
      expect(url).toBe('/api/products/import')
      expect(options?.body).toBe(form)
      expect(new Headers(options?.headers).get('X-CSRF-TOKEN')).toBe('valid')
      expect(new Headers(options?.headers).has('Content-Type')).toBe(false)
      return json({ created: 1 })
    })
    vi.stubGlobal('fetch', fetch)

    expect(await api('/products/import', { method: 'POST', body: form })).toEqual({ created: 1 })
    expect(fetch).toHaveBeenCalledTimes(2)
  })
  it('returns binary data for an Excel download', async () => {
    const bytes = new Uint8Array([80, 75, 3, 4])
    const contentType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    const fetch = vi.fn(async () => new Response(bytes, { headers: { 'Content-Type': contentType } }))
    vi.stubGlobal('fetch', fetch)

    const download = await apiBlob('/products/export')
    expect(download.type).toBe(contentType)
    expect(download.size).toBe(bytes.byteLength)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})
