import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../../App'
import { AuthProvider } from './AuthContext'
import { resetCsrf } from '../../api/client'
import type { User } from '../../types'
const account:User={id:1,login:'manager',fullName:'Иванов  Иван Иванович',email:'manager@example.by',role:'MANAGER',roleName:'Менеджер по продажам'}
const json=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}})
function setup(path='/login',authenticated=false){
  let signedIn=authenticated
  const requests:{path:string;body:Record<string,unknown>;csrf:string|null}[]=[]
  const fetch=vi.fn(async(input:RequestInfo|URL,options?:RequestInit)=>{
    const url=String(input)
    if(url==='/api/auth/csrf')return json({token:'csrf-value',headerName:'X-CSRF-TOKEN'})
    if(url==='/api/auth/providers')return json({google:false})
    if(url==='/api/auth/me')return signedIn?json(account):json({code:'UNAUTHORIZED',message:'Войдите в систему'},401)
    if(url==='/api/auth/login'||url==='/api/auth/register'){
      const body=JSON.parse(String(options?.body))
      requests.push({path:url,body,csrf:new Headers(options?.headers).get('X-CSRF-TOKEN')})
      if(body.password==='wrong-password')return json({code:'INVALID_CREDENTIALS',message:'Неверный логин или пароль'},401)
      signedIn=true;return json({...account,...(url.endsWith('/register')?{fullName:body.fullName,login:body.login,email:body.email}:{})})
    }
    if(url==='/api/auth/logout'){
      signedIn=false;requests.push({path:url,body:{},csrf:new Headers(options?.headers).get('X-CSRF-TOKEN')});return new Response(null,{status:204})
    }
    return json([])
  })
  vi.stubGlobal('fetch',fetch)
  render(<MemoryRouter initialEntries={[path]}><AuthProvider><App/></AuthProvider></MemoryRouter>)
  return {requests,fetch}
}
beforeEach(resetCsrf)
afterEach(()=>vi.unstubAllGlobals())
describe('authentication user journeys',()=>{
  it('signs in with a CSRF token, opens the profile and ends the server session',async()=>{
    const {requests}=setup()
    const user=userEvent.setup()
    await user.type(await screen.findByLabelText('Логин'),'manager')
    await user.type(screen.getByLabelText('Пароль'),'correct-password')
    await user.click(screen.getByRole('button',{name:'Войти в систему'}))
    expect(await screen.findByRole('heading',{name:'Здравствуйте, Иван Иванович'})).toBeInTheDocument()
    expect(requests[0]).toEqual({path:'/api/auth/login',body:{login:'manager',password:'correct-password'},csrf:'csrf-value'})
    await user.click(screen.getByRole('link',{name:'Открыть профиль'}))
    expect(await screen.findByRole('heading',{name:'Мой профиль'})).toBeInTheDocument()
    expect(screen.getByText('manager@example.by')).toBeInTheDocument()
    await user.click(screen.getByRole('button',{name:'Выйти из системы'}))
    expect(await screen.findByRole('heading',{name:'С возвращением'})).toBeInTheDocument()
    expect(requests.at(-1)).toMatchObject({path:'/api/auth/logout',csrf:'csrf-value'})
  })
  it('shows invalid credentials and keeps the form available',async()=>{
    setup();const user=userEvent.setup()
    await user.type(await screen.findByLabelText('Логин'),'manager')
    await user.type(screen.getByLabelText('Пароль'),'wrong-password')
    await user.click(screen.getByRole('button',{name:'Войти в систему'}))
    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный логин или пароль')
    expect(screen.getByRole('button',{name:'Войти в систему'})).toBeEnabled()
  })
  it('rejects mismatched passwords before sending registration',async()=>{
    const {requests}=setup('/register');const user=userEvent.setup()
    await user.type(await screen.findByLabelText('ФИО'),'Новый Пользователь')
    await user.type(screen.getByLabelText('Логин'),'new.manager')
    await user.type(screen.getByLabelText('Электронная почта'),'new@example.by')
    await user.type(screen.getByLabelText('Пароль'),'correct-password')
    await user.type(screen.getByLabelText('Повторите пароль'),'different-password')
    await user.click(screen.getByRole('button',{name:'Создать учётную запись'}))
    expect(await screen.findByRole('alert')).toHaveTextContent('Пароли не совпадают')
    expect(requests).toHaveLength(0)
  })
  it('registers a manager without offering privilege escalation',async()=>{
    const {requests}=setup('/register');const user=userEvent.setup()
    await user.type(await screen.findByLabelText('ФИО'),'Новый Пользователь')
    await user.type(screen.getByLabelText('Логин'),'new.manager')
    await user.type(screen.getByLabelText('Электронная почта'),'new@example.by')
    await user.type(screen.getByLabelText('Пароль'),'correct-password')
    await user.type(screen.getByLabelText('Повторите пароль'),'correct-password')
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button',{name:'Создать учётную запись'}))
    expect(await screen.findByRole('heading',{name:'Здравствуйте, Пользователь'})).toBeInTheDocument()
    expect(requests[0].body).toEqual({login:'new.manager',email:'new@example.by',fullName:'Новый Пользователь',password:'correct-password'})
  })
  it('blocks a direct administrator route for a manager',async()=>{
    const {fetch}=setup('/administration',true)
    expect(await screen.findByRole('heading',{name:'Этот раздел недоступен'})).toBeInTheDocument()
    expect(screen.queryByRole('link',{name:'Пользователи'})).not.toBeInTheDocument()
    expect(fetch.mock.calls.some(call=>call[0]==='/api/users')).toBe(false)
  })
  it('explains that Google is unavailable until configured',async()=>{
    setup()
    expect(await screen.findByRole('button',{name:'Google'})).toBeDisabled()
    await waitFor(()=>expect(screen.getByText('Вход через Google появится после настройки OAuth администратором.')).toBeInTheDocument())
  })
})
