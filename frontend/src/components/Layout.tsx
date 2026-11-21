import { useState } from 'react'
import { LogOut, Menu, X, ChevronRight, ShieldCheck } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { Brand } from './Brand'
import { navigationFor, pages, priceHistoryPage } from './navigation'
import { useAuth } from '../features/auth/AuthContext'
export function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  if (!user) return null
  const visible = navigationFor(user.role)
  const groups = [...new Set(visible.map(page => page.group))]
  const page = location.pathname === priceHistoryPage.path
    ? priceHistoryPage
    : visible.find(item => item.path === location.pathname) ?? pages.find(item => item.path === location.pathname)
  const title = page?.title ?? (location.pathname.startsWith('/plans/') ? 'Региональный план' : 'Страница')
  const initials = user.fullName.trim().split(/\s+/).slice(0,2).map(word=>word[0]).join('').toUpperCase()
  async function signOut() {
    setBusy(true); setError('')
    try { await logout() } catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось выйти из системы.') } finally { setBusy(false) }
  }
  return <div className="workspace">
    {open && <button className="sidebar-overlay" aria-label="Закрыть навигацию" onClick={()=>setOpen(false)}/>}
    <aside className={`sidebar ${open ? 'sidebar-open' : ''}`}>
      <div className="sidebar-brand"><Link to="/" onClick={()=>setOpen(false)} aria-label="Регион — главная"><Brand inverse/></Link><button className="icon-button sidebar-close" onClick={()=>setOpen(false)} aria-label="Закрыть меню"><X size={20}/></button></div>
      <nav aria-label="Основная навигация">{groups.map(group=><div className="nav-group" key={group}><span className="nav-label">{group}</span>{visible.filter(item=>item.group===group).map(item=><NavLink end={item.path==='/'} key={item.path} to={item.path} onClick={()=>setOpen(false)} className={({isActive})=>`nav-link ${isActive ? 'active' : ''}`}><item.icon size={18}/><span>{item.title}</span></NavLink>)}</div>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-security"><ShieldCheck size={17}/><span>Доступ защищён</span><span className="live-dot"/></div><button className="logout-button" onClick={()=>void signOut()} disabled={busy}><LogOut size={17}/>{busy ? 'Выходим…' : 'Выйти из системы'}</button></div>
    </aside>
    <div className="workspace-main">
      <header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-menu" onClick={()=>setOpen(true)} aria-label="Открыть меню"><Menu size={22}/></button><span>Рабочее пространство</span><ChevronRight size={14}/><strong>{title}</strong></div><Link className="user-chip" to="/profile"><div className="user-chip-text"><strong>{user.fullName}</strong><small>{user.roleName}</small></div><span className="avatar">{initials}</span></Link></header>
      <main className="page-content">{error && <div className="alert alert-error" role="alert">{error}</div>}<Outlet/></main>
      <footer className="workspace-footer"><span>Регион · Система управления сбытом</span><span>BYN · Республика Беларусь</span></footer>
    </div>
  </div>
}
