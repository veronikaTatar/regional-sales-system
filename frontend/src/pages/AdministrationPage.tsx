import { useState } from 'react'
import { MapPin, Pencil, Search, UserRoundPlus, UsersRound } from 'lucide-react'
import { api } from '../api/client'
import { Loading } from '../components/Loading'
import { RegionAssignmentsDialog } from '../features/administration/RegionAssignmentsDialog'
import { UserEditor } from '../features/administration/UserEditor'
import type { ManagedUser, RoleOption } from '../features/administration/types'
import { useAuth } from '../features/auth/AuthContext'
import { useResource } from '../hooks/useResource'
import type { Role } from '../types'
import { PageHeading } from './WorkspacePages'
import '../features/administration/administration.css'

type Editor = { kind: 'create' } | { kind: 'edit' | 'assignments'; account: ManagedUser }

export function AdministrationPage() {
  const { user, updateCurrentUser, refresh: refreshSession } = useAuth()
  const users = useResource<ManagedUser[]>('/users')
  const roles = useResource<RoleOption[]>('/roles')
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [status, setStatus] = useState('')
  const [editor, setEditor] = useState<Editor | null>(null)
  const [updating, setUpdating] = useState<number | null>(null)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  const search = query.trim().toLocaleLowerCase('ru')
  const visibleUsers = (users.data ?? []).filter(account =>
    (!role || account.role === role) &&
    (!status || account.active === (status === 'active')) &&
    [account.fullName, account.login, account.email].some(value => value?.toLocaleLowerCase('ru').includes(search)),
  )

  function resetFilters() {
    setQuery('')
    setRole('')
    setStatus('')
  }

  function openEditor(next: Editor) {
    setNotice('')
    setError('')
    setEditor(next)
  }

  function userSaved(account: ManagedUser, changedPassword: boolean) {
    setEditor(null)
    if (account.id === user?.id && changedPassword) {
      void refreshSession()
      return
    }
    updateCurrentUser(account)
    setNotice(editor?.kind === 'create'
      ? `Пользователь «${account.fullName}» создан.`
      : `Данные пользователя «${account.fullName}» сохранены.`)
    void users.refresh()
  }

  async function changeStatus(account: ManagedUser) {
    if (account.active && !window.confirm(`Заблокировать пользователя «${account.fullName}»? Его сеансы будут завершены.`)) return
    setUpdating(account.id)
    setNotice('')
    setError('')
    try {
      const saved = await api<ManagedUser>(`/users/${account.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ active: !account.active }),
      })
      setNotice(`Учётная запись «${saved.fullName}» ${saved.active ? 'активирована' : 'заблокирована'}.`)
      await users.refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось изменить статус учётной записи.')
    } finally {
      setUpdating(null)
    }
  }

  return <>
    <PageHeading title="Пользователи" subtitle="Управление учётными записями, ролями и доступом к регионам.">
      <button className="button button-primary admin-create" type="button"
        disabled={!roles.data?.length || updating !== null} onClick={() => openEditor({ kind: 'create' })}>
        <UserRoundPlus size={17}/>Создать пользователя
      </button>
    </PageHeading>

    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {error && <p className="alert alert-error" role="alert">{error}</p>}
    {(users.error || roles.error) && (
      <div className="alert alert-error" role="alert">
        {users.error || roles.error}
        <button className="text-button" type="button" onClick={() => {
          void users.refresh()
          void roles.refresh()
        }}>Повторить</button>
      </div>
    )}

    <section className="panel catalog-panel" aria-labelledby="accounts-heading" aria-busy={users.loading}>
      <div className="table-toolbar admin-toolbar">
        <h2 id="accounts-heading">
          <UsersRound size={19}/>Учётные записи
          {users.data && (
            <span className="count-pill">
              {visibleUsers.length === users.data.length ? users.data.length : `${visibleUsers.length} из ${users.data.length}`}
            </span>
          )}
        </h2>
        <div className="admin-filters">
          <label className="search-input">
            <Search size={17}/>
            <input type="search" aria-label="Поиск пользователей" placeholder="ФИО, логин или email"
              value={query} onChange={event => setQuery(event.target.value)}/>
          </label>
          <select aria-label="Фильтр по роли" value={role} onChange={event => setRole(event.target.value as Role | '')}>
            <option value="">Все роли</option>
            {roles.data?.map(option => <option key={option.code} value={option.code}>{option.name}</option>)}
          </select>
          <select aria-label="Фильтр по статусу" value={status} onChange={event => setStatus(event.target.value)}>
            <option value="">Все статусы</option>
            <option value="active">Активные</option>
            <option value="blocked">Заблокированные</option>
          </select>
        </div>
      </div>

      {users.loading && !users.data && <Loading label="Загружаем пользователей…"/>}
      {users.data?.length === 0 && <div className="table-no-results">Учётных записей пока нет.</div>}
      {!!users.data?.length && visibleUsers.length === 0 && (
        <div className="table-no-results">
          Пользователи по заданным фильтрам не найдены.
          <button className="text-button" type="button" onClick={resetFilters}>Сбросить фильтры</button>
        </div>
      )}
      {visibleUsers.length > 0 && (
        <div className="table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th scope="col">Сотрудник</th>
                <th scope="col">Логин</th>
                <th scope="col">Роль</th>
                <th scope="col">Статус</th>
                <th scope="col" className="numeric">Действия</th>
              </tr>
            </thead>
            <tbody>
              {visibleUsers.map(account => {
                const self = account.id === user?.id
                return (
                  <tr key={account.id}>
                    <td>
                      <strong>{account.fullName}</strong>
                      {self && <span className="self-label">вы</span>}
                      {account.email && <small className="cell-secondary">{account.email}</small>}
                    </td>
                    <td className="monospace">{account.login}</td>
                    <td>{account.roleName}</td>
                    <td><span className={`mini-status ${account.active ? '' : 'inactive'}`}>
                      {account.active ? 'Активен' : 'Заблокирован'}
                    </span></td>
                    <td>
                      <div className="admin-row-actions">
                        <button type="button" className="text-button" aria-label={`Редактировать ${account.login}`}
                          disabled={updating !== null || !roles.data} onClick={() => openEditor({ kind: 'edit', account })}>
                          <Pencil size={13}/>Изменить
                        </button>
                        {(account.role === 'MANAGER' || account.hasRegionAssignments) && (
                          <button type="button" className="text-button" aria-label={`Регионы ${account.login}`}
                            disabled={updating !== null} onClick={() => openEditor({ kind: 'assignments', account })}>
                            <MapPin size={14}/>Регионы
                          </button>
                        )}
                        {!self && (
                          <button type="button" className={`text-button ${account.active ? 'danger-text' : ''}`}
                            disabled={updating !== null} onClick={() => void changeStatus(account)}>
                            {updating === account.id ? 'Сохраняем…' : account.active ? 'Заблокировать' : 'Активировать'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>

    {editor?.kind === 'assignments' && (
      <RegionAssignmentsDialog account={editor.account} onClose={() => setEditor(null)}/>
    )}
    {editor && editor.kind !== 'assignments' && (
      <UserEditor account={editor.kind === 'edit' ? editor.account : null} roles={roles.data ?? []}
        self={editor.kind === 'edit' && editor.account.id === user?.id}
        onClose={() => setEditor(null)} onSaved={userSaved}/>
    )}
  </>
}
