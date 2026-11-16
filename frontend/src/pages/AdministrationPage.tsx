import { useState } from 'react'
import { Check, ShieldCheck, UsersRound } from 'lucide-react'
import type { Role, User } from '../types'
import { useResource } from '../hooks/useResource'
import { api } from '../api/client'
import { useAuth } from '../features/auth/AuthContext'
import { Loading } from '../components/Loading'
import { PageHeading } from './WorkspacePages'
interface ManagedUser extends User { active: boolean }
interface RoleOption { code: Role; name: string }
function UserRow({account,roles,onUpdated,self}: {account:ManagedUser; roles:RoleOption[]; onUpdated:()=>Promise<void>;self:boolean}) {
  const [role,setRole]=useState(account.role)
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  async function update(patch: {role?:Role;active?:boolean}) {
    if (patch.active===false && !window.confirm(`Заблокировать пользователя «${account.fullName}»? Доступ к системе будет закрыт.`)) return
    setBusy(true);setError('')
    try { await api(`/users/${account.id}`,{method:'PATCH',body:JSON.stringify(patch)}); await onUpdated() } catch(cause) {setError(cause instanceof Error ? cause.message : 'Не удалось обновить пользователя.')} finally {setBusy(false)}
  }
  return <tr><td><strong>{account.fullName}{self && <span className="self-label">вы</span>}</strong><small className="cell-secondary">{account.email??'Почта не указана'}</small>{error && <small role="alert" className="field-error">{error}</small>}</td><td className="monospace">{account.login}</td><td><div className="role-editor"><select aria-label={`Роль ${account.login}`} value={role} onChange={event=>setRole(event.target.value as Role)} disabled={busy||self}>{roles.map(option=><option key={option.code} value={option.code}>{option.name}</option>)}</select>{role!==account.role && <button className="icon-button save-role" aria-label={`Сохранить роль ${account.login}`} onClick={()=>void update({role})} disabled={busy}><Check size={17}/></button>}</div></td><td><span className={`mini-status ${account.active?'':'inactive'}`}>{account.active?'Активен':'Заблокирован'}</span></td><td className="numeric"><button className={`text-button ${account.active?'danger-text':''}`} disabled={busy||self} onClick={()=>void update({active:!account.active})}>{busy?'Сохраняем…':account.active?'Заблокировать':'Активировать'}</button></td></tr>
}
export function AdministrationPage() {
  const {user}=useAuth()
  const users=useResource<ManagedUser[]>('/users')
  const roles=useResource<RoleOption[]>('/roles')
  return <><PageHeading eyebrow="УПРАВЛЕНИЕ" title="Пользователи" subtitle="Роли сотрудников и доступ к рабочему пространству."><span className="outline-badge"><ShieldCheck size={14}/>Только для администратора</span></PageHeading><section className="panel catalog-panel"><div className="table-toolbar"><h2><UsersRound size={19}/>Учётные записи<span className="count-pill">{users.data?.length??'—'}</span></h2></div>{users.loading||roles.loading ? <Loading/> : users.error||roles.error ? <div className="alert alert-error" role="alert">{users.error||roles.error}<button className="text-button" onClick={()=>{void users.refresh();void roles.refresh()}}>Повторить</button></div> : <div className="table-scroll"><table><thead><tr><th>Сотрудник</th><th>Логин</th><th>Роль в системе</th><th>Статус</th><th className="numeric">Действия</th></tr></thead><tbody>{users.data?.map(account=><UserRow key={`${account.id}-${account.role}-${account.active}`} account={account} roles={roles.data??[]} onUpdated={users.refresh} self={account.id===user?.id}/>)}</tbody></table></div>}</section><div className="stage-notice"><ShieldCheck size={21}/><div><strong>Права проверяются сервером</strong><p>Новые сотрудники регистрируются самостоятельно с ролью менеджера. Изменить роль или заблокировать аккаунт может только администратор. Свой аккаунт нельзя заблокировать или лишить административных прав на этой странице.</p></div></div></>
}
