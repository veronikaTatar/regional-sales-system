import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { PlanDecision, PlanSummary } from './types'

const decisions = {
  APPROVE: { title: 'Утвердить региональный план', comment: 'Комментарий к утверждению', action: 'Утвердить' },
  RETURN: { title: 'Вернуть план на доработку', comment: 'Что нужно исправить', action: 'Вернуть на доработку' },
  REJECT: { title: 'Оформить отказ от плана', comment: 'Причина отказа', action: 'Оформить отказ' },
}

export function PlanReviewDialog({ plan, decision, onClose, onSaved }: {
  plan: PlanSummary
  decision: PlanDecision
  onClose: () => void
  onSaved: (plan: PlanSummary) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldError, setFieldError] = useState('')
  const labels = decisions[decision]
  const requiresReason = decision !== 'APPROVE'

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const reviewComment = String(form.get('reviewComment')).trim()
    const reviewDecision = decision === 'RETURN' && form.get('recipient') === 'analyst' ? 'RETURN_ANALYSIS' : decision
    setError('')
    setFieldError('')
    if (requiresReason && !reviewComment) {
      setFieldError(decision === 'RETURN' ? 'Укажите, что нужно исправить' : 'Укажите причину отказа')
      return
    }
    setBusy(true)
    try {
      const updated = await api<PlanSummary>('/plans/' + plan.id + '/review', {
        method: 'POST', body: JSON.stringify({ version: plan.version, decision: reviewDecision, reviewComment }),
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
    <AdminDialog title={labels.title} description={plan.regionName}
      busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {decision === 'RETURN' && <label className="admin-field">
          Ответственный за доработку
          <select name="recipient" disabled={busy}>
            <option value="manager">Менеджер — позиции проекта</option>
            <option value="analyst">Аналитик — расчёт и рекомендация</option>
          </select>
        </label>}
        <label className="admin-field">
          {labels.comment}
          <textarea name="reviewComment" rows={4} className="product-description" maxLength={2000} required={requiresReason}
            disabled={busy} aria-invalid={!!fieldError}/>
          {fieldError && <small className="field-error">{fieldError}</small>}
        </label>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Сохраняем…' : labels.action}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
