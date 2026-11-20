import { ArrowRight, Check, CheckCircle2, Clock3, Database, Fingerprint, LockKeyhole, MapPinned, ShieldCheck, UserRound, Layers3 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { PlanAttention } from '../features/plans/PlanAttention'
import { pages, planDetailRoles } from '../components/navigation'
import type { PageDefinition } from '../components/navigation'
export function PageHeading({ eyebrow, title, subtitle, children }: { eyebrow?: string; title: string; subtitle: string; children?: React.ReactNode }) {
  return <div className="page-heading"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1><p>{subtitle}</p></div>{children}</div>
}
export function Dashboard() {
  const { user } = useAuth()
  if (!user) return null
  const name = user.fullName.split(' ')[0]
  const roleDescriptions = { ADMIN: 'Управляйте учётными записями, назначайте роли и контролируйте доступ сотрудников.', MANAGER: 'Ваши регионы, продукция и продажи — в едином рабочем пространстве.', ANALYST: 'Превращайте региональную статистику в основу обоснованных решений.', SALES_HEAD: 'Планируйте региональные продажи и контролируйте результаты команды.' }
  const available = pages.filter(page => page.roles.includes(user.role) && !['/','/profile'].includes(page.path))
  return <>
    <PageHeading eyebrow="ОБЗОР СИСТЕМЫ" title={`Здравствуйте, ${name}`} subtitle="Добро пожаловать в систему управления региональным сбытом."><span className="status-badge"><span className="live-dot"/>Учётная запись активна</span></PageHeading>
    {planDetailRoles.includes(user.role) && <PlanAttention/>}
    <section className="welcome-banner"><div><span className="eyebrow light">ВАША РОЛЬ · {user.roleName.toUpperCase()}</span><h2>Всё начинается<br/>с общей картины.</h2><p>{roleDescriptions[user.role]}</p><Link to="/profile" className="banner-link">Открыть профиль<ArrowRight size={17}/></Link></div><div className="welcome-art" aria-hidden="true"><div className="art-ring ring-one"/><div className="art-ring ring-two"/><div className="art-ring ring-three"/><MapPinned size={64} strokeWidth={1}/><span className="art-label">РЕСПУБЛИКА БЕЛАРУСЬ</span></div></section>
    <div className="section-title"><h2>Основа для вашей работы</h2><span>Первый этап</span></div>
    <div className="foundation-grid">{[{icon:ShieldCheck,title:'Безопасный доступ',text:'Логин, пароль и Google. Полномочия проверяются на сервере.'},{icon:Database,title:'Единая база данных',text:'Модель продукции, регионов, статистики и планирования.'},{icon:UserRound,title:'Ваша учётная запись',text:'Профиль сотрудника и доступ в соответствии с назначенной ролью.'}].map(card=><article className="foundation-card" key={card.title}><span className="card-icon"><card.icon size={22}/></span><h3>{card.title}</h3><p>{card.text}</p></article>)}</div>
    <div className="section-title"><h2>Ваши разделы</h2><span>{available.length} доступно</span></div>
    <div className="quick-links">{available.map(page=><Link key={page.path} to={page.path} className="quick-link"><page.icon size={20}/><span>{page.title}</span><ArrowRight size={16}/></Link>)}</div>
  </>
}
export function PlaceholderPage({ page }: { page: PageDefinition }) {
  return <><PageHeading eyebrow={page.group} title={page.title} subtitle={page.subtitle}/><section className="placeholder-panel"><div className="placeholder-illustration"><span/><page.icon size={46} strokeWidth={1.3}/><span/></div><span className="outline-badge"><Clock3 size={13}/>Следующий этап</span><h2>Раздел подготовлен к разработке</h2><p>Структура данных и место в системе уже определены.<br/>Функции этого раздела будут добавлены на следующем этапе.</p><div className="planned-capabilities">{page.capabilities?.map((text,index)=><div key={text}><span>{String(index+1).padStart(2,'0')}</span><p>{text}</p></div>)}</div><Link to="/" className="button button-secondary">На главную<ArrowRight size={16}/></Link></section><div className="subtle-note"><Layers3 size={16}/>Этот экран — заглушка. Данные и результаты операций не имитируются.</div></>
}
export function ProfilePage() {
  const { user } = useAuth()
  if (!user) return null
  return <><PageHeading eyebrow="УЧЁТНАЯ ЗАПИСЬ" title="Мой профиль" subtitle="Информация о вашей учётной записи и доступе к системе."/><div className="profile-grid"><section className="panel profile-summary"><span className="profile-avatar">{user.fullName.split(/\s+/).slice(0,2).map(word=>word[0]).join('').toUpperCase()}</span><h2>{user.fullName}</h2><p>@{user.login}</p><span className="role-pill">{user.roleName}</span><div className="profile-active"><CheckCircle2 size={16}/>Учётная запись активна</div></section><section className="panel profile-details"><h2>Основная информация</h2><dl><div><dt>ФИО</dt><dd>{user.fullName}</dd></div><div><dt>Логин</dt><dd>{user.login}</dd></div><div><dt>Электронная почта</dt><dd>{user.email ?? 'Не указана'}</dd></div><div><dt>Роль в системе</dt><dd>{user.roleName}</dd></div></dl><p className="form-note">Для изменения данных или роли обратитесь к администратору системы.</p></section></div><section className="panel access-panel"><span className="card-icon"><LockKeyhole size={22}/></span><div><h2>Доступ под контролем</h2><p>Права определяются вашей ролью. Для менеджеров коммерческие данные дополнительно ограничены закреплёнными регионами.</p></div><Fingerprint className="access-fingerprint" size={60} strokeWidth={1}/></section><div className="subtle-note"><Check size={16}/>Данные профиля загружены из вашей серверной сессии.</div></>
}
export function ForbiddenPage() { return <div className="error-page"><span className="card-icon"><LockKeyhole size={30}/></span><span className="eyebrow">403 · ДОСТУП ОГРАНИЧЕН</span><h1>Этот раздел недоступен</h1><p>У вашей роли нет прав для просмотра этой страницы.<br/>При необходимости обратитесь к администратору.</p><Link to="/" className="button button-primary">На главную<ArrowRight size={16}/></Link></div> }
export function NotFoundPage() { return <div className="error-page"><span className="eyebrow">404 · СТРАНИЦА НЕ НАЙДЕНА</span><h1>Здесь пока ничего нет</h1><p>Проверьте адрес страницы или вернитесь в рабочее пространство.</p><Link to="/" className="button button-primary">На главную<ArrowRight size={16}/></Link></div> }
