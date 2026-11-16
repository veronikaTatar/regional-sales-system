import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, Check, Eye, EyeOff, ShieldCheck } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { api, ApiError } from '../api/client'
import { useAuth } from '../features/auth/AuthContext'

function GoogleMark() {
  return <svg width="19" height="19" viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.02v2.51h3.23c1.89-1.74 2.99-4.3 2.99-7.36Z"/><path fill="#34A853" d="M12 22c2.7 0 4.96-.9 6.61-2.41l-3.23-2.51c-.9.6-2.05.97-3.38.97-2.61 0-4.83-1.76-5.62-4.13H3.04v2.6A10 10 0 0 0 12 22Z"/><path fill="#FBBC05" d="M6.38 13.92a6 6 0 0 1 0-3.84v-2.6H3.04a10 10 0 0 0 0 9.04l3.34-2.6Z"/><path fill="#EA4335" d="M12 5.95c1.47 0 2.79.51 3.83 1.5l2.87-2.87A9.6 9.6 0 0 0 12 2a10 10 0 0 0-8.96 5.48l3.34 2.6C7.17 7.71 9.39 5.95 12 5.95Z"/></svg>
}
export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register, startupError } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [google, setGoogle] = useState(false)
  const [providerLoaded, setProviderLoaded] = useState(false)
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const isRegister = mode === 'register'
  useEffect(() => {
    let current = true
    api<{ google: boolean }>('/auth/providers').then(result => { if (current) setGoogle(result.google) }).catch(() => {}).finally(() => { if (current) setProviderLoaded(true) })
    return () => { current = false }
  }, [])
  useEffect(() => { setError(''); setFields({}); setVisible(false) }, [mode])
  if (user) return <Navigate to="/" replace/>
  const googleFailed = new URLSearchParams(location.search).get('error') === 'google'
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const password = String(form.get('password') ?? '')
    setError(''); setFields({})
    if (new TextEncoder().encode(password).length > 72) { setError('Пароль должен занимать не более 72 байт. Для кириллицы и символов это может быть меньше 72 знаков.'); return }
    if (isRegister && password !== form.get('confirmPassword')) { setError('Пароли не совпадают. Проверьте повторный ввод.'); return }
    setBusy(true)
    try {
      const loginValue = String(form.get('login')).trim()
      if (isRegister) await register({ login: loginValue, password, email: String(form.get('email')).trim(), fullName: String(form.get('fullName')).trim() })
      else await login(loginValue, password)
      const from = (location.state as { from?: string } | null)?.from
      void navigate(from?.startsWith('/') && !from.startsWith('//') ? from : '/', { replace: true })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось войти в систему.')
      if (cause instanceof ApiError) setFields(cause.fieldErrors)
    } finally { setBusy(false) }
  }
  return <main className="auth-layout">
    <aside className="auth-story">
      <Brand inverse/>
      <div className="auth-story-main"><span className="eyebrow light">РЕГИОНАЛЬНАЯ СБЫТОВАЯ ПОЛИТИКА</span><h1>Каждый регион.<br/>Одна система.</h1><p>От возможностей регионального рынка<br className="desktop-break"/> к взвешенным решениям в продажах.</p>
        <div className="territory" aria-hidden="true"><div className="territory-grid"/><svg viewBox="0 0 440 285"><path className="territory-outline" d="M75 119 86 77 139 62 170 36 223 47 256 24 291 47 331 55 340 88 371 111 365 147 391 179 365 214 323 211 288 244 243 239 220 257 187 242 157 251 128 228 87 224 78 193 47 173 61 143Z"/><path className="territory-line" d="m86 77 93 63-22 111m22-111 77-116m-77 116 161-52m-161 52 64 99m-64-99-132 33m132-33 186 74"/>{[[117,185],[147,100],[224,145],[273,80],[320,155],[284,209],[211,168]].map(([x,y],i)=><g key={i}><circle className="territory-halo" cx={x} cy={y} r={i===2?14:8}/><circle className="territory-dot" cx={x} cy={y} r={i===2?5:3}/></g>)}<text x="238" y="144">Минск</text><text x="88" y="203">Брест</text><text x="270" y="65">Витебск</text><text x="294" y="230">Гомель</text><text x="95" y="88">Гродно</text><text x="330" y="146">Могилёв</text></svg><div className="territory-caption"><span className="live-dot"/>Республика Беларусь<span>7 регионов</span></div></div>
        <div className="story-features"><span><Check size={16}/>Единое пространство данных</span><span><Check size={16}/>Доступ по ролям и регионам</span></div>
      </div>
      <p className="story-foot">Система управления региональным сбытом · 2026</p>
    </aside>
    <section className="auth-content">
      <div className="auth-mobile-brand"><Brand/></div>
      <div className={`auth-card ${isRegister ? 'registration-card' : ''}`}>
        <div className="auth-tabs"><Link to="/login" className={!isRegister ? 'selected' : ''}>Вход</Link><Link to="/register" className={isRegister ? 'selected' : ''}>Регистрация</Link></div>
        <span className="eyebrow">ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО</span>
        <h2>{isRegister ? 'Создайте учётную запись' : 'С возвращением'}</h2>
        <p className="auth-intro">{isRegister ? 'Начните работу с региональными продажами.' : 'Войдите, чтобы продолжить работу с системой.'}</p>
        {(error || googleFailed || startupError) && <div className="alert alert-error" role="alert">{error || (googleFailed ? 'Не удалось войти через Google. Повторите попытку или используйте логин и пароль.' : startupError)}</div>}
        <form onSubmit={event => void submit(event)} key={mode}>
          {isRegister && <label>ФИО<input name="fullName" placeholder="Иванов Иван Иванович" autoComplete="name" required minLength={2} maxLength={150} disabled={busy}/>{fields.fullName && <small className="field-error">{fields.fullName}</small>}</label>}
          <label>Логин<input name="login" placeholder="Введите логин" autoComplete="username" required minLength={isRegister ? 3 : 1} maxLength={60} pattern={isRegister ? '[a-zA-Z0-9._\\-]+' : undefined} title="Латинские буквы, цифры, точка, дефис и подчёркивание" disabled={busy}/>{fields.login && <small className="field-error">{fields.login}</small>}</label>
          {isRegister && <label>Электронная почта<input type="email" name="email" placeholder="name@company.by" autoComplete="email" required maxLength={254} disabled={busy}/>{fields.email && <small className="field-error">{fields.email}</small>}</label>}
          <label>Пароль<div className="password-input"><input name="password" type={visible ? 'text' : 'password'} placeholder={isRegister ? 'Не менее 8 символов' : 'Введите пароль'} autoComplete={isRegister ? 'new-password' : 'current-password'} required minLength={isRegister ? 8 : 1} maxLength={72} disabled={busy}/><button type="button" aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div>{fields.password && <small className="field-error">{fields.password}</small>}</label>
          {isRegister && <label>Повторите пароль<input name="confirmPassword" type={visible ? 'text' : 'password'} placeholder="Введите пароль ещё раз" autoComplete="new-password" required minLength={8} maxLength={72} disabled={busy}/></label>}
          {isRegister && <p className="form-note">Будет создана учётная запись менеджера. Доступ к регионам и дополнительные права предоставляет администратор.</p>}
          <button className="button button-primary auth-submit" disabled={busy} type="submit">{busy ? <><span className="spinner small"/>{isRegister ? 'Создаём аккаунт…' : 'Входим…'}</> : <>{isRegister ? 'Создать учётную запись' : 'Войти в систему'}<ArrowRight size={18}/></>}</button>
        </form>
        <div className="divider"><span>или продолжить с</span></div>
        <button className="button google-button" type="button" disabled={!google || busy} onClick={() => window.location.assign('/oauth2/authorization/google')}><GoogleMark/>Google</button>
        {providerLoaded && !google && <p className="provider-note">Вход через Google появится после настройки OAuth администратором.</p>}
        <p className="secure-note"><ShieldCheck size={15}/>Защищённая сессия · Доступ по ролям</p>
      </div>
      <p className="auth-copyright">Регион · Управление сбытом предприятия</p>
    </section>
  </main>
}
