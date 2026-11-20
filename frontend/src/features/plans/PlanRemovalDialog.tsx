import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import { formatPeriod } from './planFormat'
import type { PlanSummary } from './types'

export function PlanRemovalDialog({ plan, action, onClose, onSaved }: {
  plan: PlanSummary
  action: 'cancel' | 'delete'
  onClose: () => void
  onSaved: () => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const removing = action === 'delete'
  const label = removing ? 'Удалить план' : 'Отменить план'

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const path = '/plans/' + plan.id + (removing ? '' : '/cancel') + '?version=' + plan.version
      await api(path, { method: removing ? 'DELETE' : 'POST' })
      await onSaved()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось выполнить действие.')
    } finally {
      setBusy(false)
    }
  }

  return <AdminDialog title={label + '?'} description={plan.regionName + ' · ' + formatPeriod(plan.periodStart, plan.periodEnd)}
    busy={busy} onClose={onClose}>
    <form className="admin-form" onSubmit={event => void save(event)}>
      {error && <div className="alert alert-error" role="alert">{error}</div>}
      <p className="plan-removal-description">{removing ? 'План и его товарные позиции будут удалены.'
        : 'План останется в списке со статусом «Отменён». Для этого региона и периода можно будет создать новый план.'}</p>
      <footer className="admin-dialog-actions">
        <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Назад</button>
        <button type="submit" className="button button-secondary danger-text plan-removal-confirm" disabled={busy}>
          {busy ? 'Сохраняем…' : label}
        </button>
      </footer>
    </form>
  </AdminDialog>
}
