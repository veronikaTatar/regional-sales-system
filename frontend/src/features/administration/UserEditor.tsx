import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import type { Role } from '../../types'
import { AdminDialog } from './AdminDialog'
import type { ManagedUser, RoleOption } from './types'

interface Props {
  account: ManagedUser | null
  roles: RoleOption[]
  self: boolean
  onClose: () => void
  onSaved: (user: ManagedUser, changedPassword: boolean) => void
}

export function UserEditor({ account, roles, self, onClose, onSaved }: Props) {
  const [changePassword, setChangePassword] = useState(!account)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fullName = String(form.get('fullName'))
    const login = String(form.get('login'))
    const email = String(form.get('email'))
    const role = self ? account?.role : form.get('role') as Role
    const password = changePassword ? String(form.get('password')) : undefined
    setError('')
    setFieldErrors({})

    if (changePassword && password !== form.get('confirmPassword')) {
      setFieldErrors({ confirmPassword: 'Пароли не совпадают' })
      return
    }

    const changes = {
      fullName: fullName !== account?.fullName ? fullName.trim() : undefined,
      login: login !== account?.login ? login : undefined,
      email: email !== account?.email ? email.trim() : undefined,
      role: role !== account?.role ? role : undefined,
      password,
    }
    if (account && Object.values(changes).every(value => value === undefined)) {
      setError('Измените хотя бы одно поле.')
      return
    }

    if (account && (changes.role || password)) {
      const newRole = roles.find(option => option.code === changes.role)?.name
      const roleNotice = newRole ? `Новая роль — «${newRole}».` : ''
      const regionNotice = changes.role === 'MANAGER' && account.hasRegionAssignments
        ? 'Сохранённые назначения будут определять доступ к регионам.' : ''
      const passwordNotice = password ? 'Прежние сеансы будут завершены.' : ''
      const signInNotice = self && password ? 'Войдите снова с новым паролем.' : ''
      if (!window.confirm([
        `Сохранить изменения для «${account.fullName}»?`, roleNotice, regionNotice, passwordNotice, signInNotice,
      ].filter(Boolean).join('\n'))) return
    }

    setBusy(true)
    try {
      const saved = await api<ManagedUser>(account ? `/users/${account.id}` : '/users', {
        method: account ? 'PATCH' : 'POST',
        body: JSON.stringify(changes),
      })
      onSaved(saved, Boolean(password))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить учётную запись.')
      if (cause instanceof ApiError) setFieldErrors(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog
      title={account ? 'Редактирование пользователя' : 'Новый пользователь'}
      description={account ? `@${account.login}` : 'Укажите данные сотрудника и выберите его роль.'}
      busy={busy}
      onClose={onClose}
    >
      <form className="admin-form" onSubmit={event => void submit(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <fieldset className="admin-form-grid" disabled={busy}>
          <label className="admin-field admin-field-wide">
            ФИО
            <input name="fullName" defaultValue={account?.fullName} required minLength={2} maxLength={150}
              autoComplete="off" aria-invalid={Boolean(fieldErrors.fullName)} autoFocus/>
            {fieldErrors.fullName && <small className="field-error">{fieldErrors.fullName}</small>}
          </label>
          <label className="admin-field">
            Логин
            <input name="login" defaultValue={account?.login} required minLength={3} maxLength={60}
              pattern={'[a-zA-Z0-9._\\-]+'} title="Латинские буквы, цифры, точка, дефис и подчёркивание"
              autoComplete="off" aria-invalid={Boolean(fieldErrors.login)}/>
            {fieldErrors.login && <small className="field-error">{fieldErrors.login}</small>}
          </label>
          <label className="admin-field">
            Электронная почта
            <input name="email" type="email" defaultValue={account?.email ?? ''} required maxLength={254}
              autoComplete="off" aria-invalid={Boolean(fieldErrors.email)}/>
            {fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Роль
            <select name="role" defaultValue={account?.role ?? 'MANAGER'} disabled={self} required>
              {roles.map(option => <option key={option.code} value={option.code}>{option.name}</option>)}
            </select>
            {fieldErrors.role && <small className="field-error">{fieldErrors.role}</small>}
          </label>
          {account && (
            <label className="admin-checkbox admin-field-wide">
              <input type="checkbox" checked={changePassword} onChange={event => setChangePassword(event.target.checked)}/>
              Задать новый пароль
            </label>
          )}
          {changePassword && <>
            <label className="admin-field">
              {account ? 'Новый пароль' : 'Пароль'}
              <input name="password" type="password" autoComplete="new-password" required minLength={8}
                maxLength={72} placeholder="Не менее 8 символов" aria-invalid={Boolean(fieldErrors.password)}/>
              {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
            </label>
            <label className="admin-field">
              Повторите пароль
              <input name="confirmPassword" type="password" autoComplete="new-password" required minLength={8}
                maxLength={72} aria-invalid={Boolean(fieldErrors.confirmPassword)}/>
              {fieldErrors.confirmPassword && <small className="field-error" role="alert">{fieldErrors.confirmPassword}</small>}
            </label>
          </>}
        </fieldset>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Сохраняем…' : account ? 'Сохранить изменения' : 'Создать пользователя'}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
