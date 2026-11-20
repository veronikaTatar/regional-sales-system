import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { PlanSummary } from './types'

export function PlanReviewDialog({ plan, decision, onClose, onSaved }: {
  plan: PlanSummary
  decision: 'APPROVE' | 'RETURN'
  onClose: () => void
  onSaved: (plan: PlanSummary) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldError, setFieldError] = useState('')
  const returning = decision === 'RETURN'

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const reviewComment = String(form.get('reviewComment')).trim()
    setError('')
    setFieldError('')
    if (returning && !reviewComment) {
      setFieldError('Укажите, что нужно исправить')
      return
    }
    setBusy(true)
    try {
      const updated = await api<PlanSummary>('/plans/' + plan.id + '/review', {
        method: 'POST', body: JSON.stringify({ version: plan.version, decision, reviewComment }),
      })
      await onSaved(updated)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить решение.')
      if (cause instanceof ApiError) setFieldError(cause.fieldErrors.reviewComment ?? '')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title={returning ? 'Вернуть план на доработку' : 'Утвердить региональный план'} description={plan.regionName}
      busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <label className="admin-field">
          {returning ? 'Что нужно исправить' : 'Комментарий к утверждению'}
          <textarea name="reviewComment" rows={4} className="product-description" maxLength={2000} required={returning}
            disabled={busy} aria-invalid={!!fieldError}/>
          {fieldError && <small className="field-error">{fieldError}</small>}
        </label>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Сохраняем…' : returning ? 'Вернуть на доработку' : 'Утвердить'}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
