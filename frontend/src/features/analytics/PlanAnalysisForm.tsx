import { useState } from 'react'
import type { FormEvent } from 'react'
import { Calculator, Send } from 'lucide-react'
import { api, ApiError } from '../../api/client'
import { normalizeDecimal, planCents } from '../plans/planFormat'
import type { PlanSummary } from '../plans/types'
import { AnalysisMetrics, AnalysisRanking, indicatorFormat, TrainingSource } from './AnalysisResults'
import type { Estimate, Weights } from './types'

type AnalysisAction = 'preview' | 'publish'

const weightFields: { key: keyof Weights; label: string }[] = [
  { key: 'salary', label: 'Средняя зарплата, %' },
  { key: 'retail', label: 'Товарооборот на жителя, %' },
  { key: 'population', label: 'Население, %' },
]

export function PlanAnalysisForm({ plan, onSaved, onReload }: {
  plan: PlanSummary
  onSaved: (plan: PlanSummary) => Promise<void>
  onReload: () => Promise<void>
}) {
  const [weights, setWeights] = useState({ salary: '40', retail: '40', population: '20' })
  const [estimate, setEstimate] = useState<Estimate | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState<AnalysisAction | null>(null)
  const [error, setError] = useState<{ action: AnalysisAction; message: string; stale?: boolean } | null>(null)
  const stale = !!error?.stale
  const values = [weights.salary, weights.retail, weights.population].map(value => normalizeDecimal(value, 3, 2))
  const total = values.every(value => value !== null)
    ? values.reduce<bigint>((sum, value) => sum + planCents(value ?? '0'), 0n) : null
  const valid = total === 10000n
  const path = '/plans/' + plan.id + '/analysis'

  async function save(event: FormEvent<HTMLFormElement>, action: AnalysisAction) {
    event.preventDefault()
    if (busy || stale) return
    setError(null)
    if (!valid) {
      setError({ action, message: 'Укажите три неотрицательных веса с суммой 100 %' })
      return
    }
    if (action === 'publish' && (!estimate || !note.trim())) {
      setError({ action, message: 'Выполните расчёт и добавьте вывод для менеджера' })
      return
    }
    const body = { version: plan.version, weights: { salary: Number(values[0]), retail: Number(values[1]), population: Number(values[2]) } }
    setBusy(action)
    try {
      if (action === 'preview') {
        setEstimate(await api<Estimate>(path + '/preview', { method: 'POST', body: JSON.stringify(body) }))
      } else {
        const updated = await api<PlanSummary>(path, {
          method: 'PUT', body: JSON.stringify({ ...body, recommendationText: note.trim() }),
        })
        await onSaved(updated)
      }
    } catch (cause) {
      setError({ action, message: cause instanceof Error ? cause.message : 'Не удалось завершить действие.',
        stale: cause instanceof ApiError && cause.code === 'PLAN_CHANGED' })
    } finally {
      setBusy(null)
    }
  }

  const errorAlert = error && <div className="alert alert-error analysis-form-alert" role="alert">
    {error.message}{stale && <button type="button" className="text-button" onClick={() => void onReload()}>Обновить задание</button>}
  </div>

  return <>
    <section className="panel plan-parameters analysis-section" aria-labelledby="analysis-form-title">
      <div className="plan-parameters-heading"><h2 id="analysis-form-title">Расчёт покупательной способности</h2><TrainingSource/></div>
      <form className="analysis-form" onSubmit={event => void save(event, 'preview')}>
        <fieldset className="analysis-weights" disabled={busy !== null || stale}>
          {weightFields.map(({ key, label }) => <label key={key} className="admin-field">
            {label}
            <input name={key} inputMode="decimal" maxLength={6} value={weights[key]} required onChange={event => {
              setWeights(previous => ({ ...previous, [key]: event.target.value }))
              setEstimate(null)
              setError(null)
            }}/>
          </label>)}
        </fieldset>
        {error?.action === 'preview' && errorAlert}
        <div className="analysis-actions">
          <p className="analysis-weight-total" aria-live="polite">Сумма весов {total === null ? '—' : indicatorFormat.format(Number(total) / 100) + ' %'}</p>
          <button type="submit" className="button button-primary" disabled={busy !== null || stale}>
            <Calculator size={16} aria-hidden="true"/>{busy === 'preview' ? 'Рассчитываем…' : 'Рассчитать индекс'}
          </button>
        </div>
      </form>
    </section>
    {estimate && <>
      <section className="panel plan-parameters analysis-section" aria-label="Результат расчёта по региону плана">
        <div className="plan-parameters-heading"><h2>{plan.regionName}</h2></div>
        <AnalysisMetrics index={estimate.selected.index} share={estimate.selected.sharePercent} adjustment={estimate.priceAdjustmentPercent}/>
      </section>
      <AnalysisRanking calculation={estimate.calculation} selectedRegionId={plan.regionId}/>
      <section className="panel plan-parameters analysis-section" aria-label="Подготовка рекомендации">
        <form className="analysis-form" onSubmit={event => void save(event, 'publish')}>
          <label className="admin-field">
            Рекомендация для менеджера
            <textarea className="product-description" name="recommendationText" placeholder="Укажите, по каким товарам стоит изменить объём или цену"
              rows={4} maxLength={2000} required value={note} onChange={event => setNote(event.target.value)} disabled={busy !== null || stale}/>
          </label>
          {error?.action === 'publish' && errorAlert}
          <div className="analysis-actions analysis-actions-end">
            <button type="submit" className="button button-primary" disabled={busy !== null || stale}>
              <Send size={16} aria-hidden="true"/>{busy === 'publish' ? 'Передаём…' : 'Сохранить и передать менеджеру'}
            </button>
          </div>
        </form>
      </section>
    </>}
  </>
}
