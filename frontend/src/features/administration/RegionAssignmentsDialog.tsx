import { useState } from 'react'
import type { FormEvent } from 'react'
import { Plus } from 'lucide-react'
import { api, ApiError } from '../../api/client'
import { Loading } from '../../components/Loading'
import { useResource } from '../../hooks/useResource'
import type { Region } from '../../types'
import { AdminDialog } from './AdminDialog'
import type { ManagedUser, RegionAssignment } from './types'

const minDate = '1000-01-01'
const maxDate = '9999-12-31'
const belarusDate = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Minsk', dateStyle: 'short' })
const displayDate = new Intl.DateTimeFormat('ru-BY', {
  timeZone: 'UTC', day: '2-digit', month: '2-digit', year: 'numeric',
})
const formatDate = (value: string) => displayDate.format(new Date(`${value}T00:00:00Z`))

interface AssignmentDraft {
  assignment?: RegionAssignment
}

export function RegionAssignmentsDialog({ account, onClose }: { account: ManagedUser; onClose: () => void }) {
  const endpoint = `/users/${account.id}/region-assignments`
  const assignments = useResource<RegionAssignment[]>(endpoint)
  const regions = useResource<Region[]>('/regions')
  const [draft, setDraft] = useState<AssignmentDraft | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState('')
  const today = belarusDate.format(new Date())
  const existing = draft?.assignment
  const canAssign = account.role === 'MANAGER'

  function openEditor(value: AssignmentDraft | null) {
    setDraft(value)
    setFieldErrors({})
    setError('')
    setNotice('')
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const body = {
      regionId: Number(form.get('regionId')),
      startDate: form.get('startDate'),
      endDate: form.get('endDate') || null,
    }
    setBusy(true)
    setError('')
    setFieldErrors({})
    try {
      await api<RegionAssignment>(existing ? `${endpoint}/${existing.id}` : endpoint, {
        method: existing ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      })
      setDraft(null)
      setNotice(existing ? 'Назначение обновлено.' : 'Регион закреплён за менеджером.')
      await assignments.refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить назначение.')
      if (cause instanceof ApiError) setFieldErrors(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  async function remove(assignment: RegionAssignment) {
    if (!window.confirm(`Отменить назначение «${assignment.regionName}» для «${account.fullName}»?`)) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await api<void>(`${endpoint}/${assignment.id}`, { method: 'DELETE' })
      setNotice('Назначение отменено.')
      await assignments.refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось отменить назначение.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title="Региональные назначения" description={account.fullName} busy={busy} onClose={onClose}>
      <div className="assignment-dialog-content">
        {!canAssign && <p className="form-note">Назначения действуют для пользователей с ролью «Менеджер по продажам».</p>}
        {canAssign && !account.active && <p className="form-note">Доступ по действующим назначениям восстановится при активации учётной записи.</p>}
        {notice && <p className="alert admin-success" role="status">{notice}</p>}
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {assignments.error && (
          <div className="alert alert-error" role="alert">
            {assignments.error}
            <button className="text-button" onClick={() => void assignments.refresh()}>Повторить</button>
          </div>
        )}
        {regions.error && (
          <div className="alert alert-error" role="alert">
            Справочник регионов недоступен. {regions.error}
            <button className="text-button" onClick={() => void regions.refresh()}>Повторить</button>
          </div>
        )}
        {assignments.loading && !assignments.data && <Loading label="Загружаем назначения…"/>}
        {assignments.data?.length === 0 && <p className="assignment-empty">У пользователя пока нет закреплённых регионов.</p>}
        {!!assignments.data?.length && (
          <ul className="assignment-list">
            {assignments.data.map(assignment => {
              const expired = assignment.endDate !== null && assignment.endDate < today
              const scheduled = assignment.startDate > today
              const status = !assignment.regionActive ? 'Регион неактивен' : expired ? 'Завершено'
                : !canAssign ? 'Роль изменена' : scheduled ? 'Запланировано'
                : account.active ? 'Действует' : 'Ожидает активации'
              return (
                <li key={assignment.id} className="assignment-item">
                  <div className="assignment-details">
                    <strong>{assignment.regionName}</strong>
                    <span>С {formatDate(assignment.startDate)}{assignment.endDate ? ` по ${formatDate(assignment.endDate)}` : ', бессрочно'}</span>
                    <span className={`mini-status ${status === 'Действует' ? '' : 'inactive'}`}>{status}</span>
                  </div>
                  <div className="admin-row-actions">
                    {canAssign && <button type="button" className="text-button" disabled={busy || !!draft || !regions.data}
                      aria-label={`Изменить назначение ${assignment.regionName}`}
                      onClick={() => openEditor({ assignment })}>Изменить</button>}
                    <button type="button" className="text-button danger-text" disabled={busy || !!draft}
                      aria-label={`Отменить назначение ${assignment.regionName}`}
                      onClick={() => void remove(assignment)}>Отменить</button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
        {draft ? (
          <form className="admin-form assignment-form" onSubmit={event => void save(event)}>
            <h3>{existing ? 'Изменить назначение' : 'Назначить регион'}</h3>
            <fieldset className="admin-form-grid" disabled={busy}>
              <label className="admin-field admin-field-wide">
                Регион
                <select name="regionId" defaultValue={existing?.regionId ?? ''} required autoFocus>
                  <option value="" disabled>Выберите регион</option>
                  {existing && !regions.data?.some(region => region.id === existing.regionId) && (
                    <option value={existing.regionId}>{existing.regionName}</option>
                  )}
                  {regions.data?.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
                </select>
                {fieldErrors.regionId && <small className="field-error">{fieldErrors.regionId}</small>}
              </label>
              <label className="admin-field">
                Дата начала
                <input type="date" name="startDate" defaultValue={existing?.startDate ?? today}
                  min={minDate} max={maxDate} required aria-invalid={Boolean(fieldErrors.startDate)}/>
                {fieldErrors.startDate && <small className="field-error">{fieldErrors.startDate}</small>}
              </label>
              <label className="admin-field">
                Дата окончания
                <input type="date" name="endDate" defaultValue={existing?.endDate ?? ''}
                  min={minDate} max={maxDate} aria-invalid={Boolean(fieldErrors.endDate)}/>
                {fieldErrors.endDate && <small className="field-error">{fieldErrors.endDate}</small>}
              </label>
            </fieldset>
            <p className="form-note">Дата окончания включается в период. Оставьте её пустой для бессрочного назначения.</p>
            <footer className="admin-dialog-actions">
              <button type="button" className="button button-secondary" onClick={() => openEditor(null)} disabled={busy}>Отмена</button>
              <button type="submit" className="button button-primary" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить назначение'}</button>
            </footer>
          </form>
        ) : (
          <footer className="admin-dialog-actions">
            <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Готово</button>
            {canAssign && <button type="button" className="button button-primary" onClick={() => openEditor({})}
              disabled={busy || !assignments.data || !regions.data?.length}><Plus size={16}/>Назначить регион</button>}
          </footer>
        )}
      </div>
    </AdminDialog>
  )
}
