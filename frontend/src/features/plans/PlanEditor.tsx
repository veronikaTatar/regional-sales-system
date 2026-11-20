import { useState } from 'react'
import type { FocusEvent, FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { CalendarPeriodFields, initialPeriod, periodDates } from '../../components/CalendarPeriodFields'
import { Loading } from '../../components/Loading'
import { useResource } from '../../hooks/useResource'
import { AdminDialog } from '../administration/AdminDialog'
import { formatPeriod, formatPlanMoney, normalizeDecimal, normalizeMoney } from './planFormat'
import type { PlanDetail, PlanRegion, PlanSummary } from './types'

export function PlanEditor({ detail, onClose, onSaved }: {
  detail?: PlanDetail
  onClose: () => void
  onSaved: (plan: PlanSummary) => void
}) {
  const plan = detail?.plan
  const options = useResource<PlanRegion[]>('/plans/options')
  const selection = initialPeriod(plan?.periodStart, plan?.periodEnd)
  const initialDates = periodDates(selection)
  const assigned = !!plan?.sentToManagerAt
  const canRequestAnalysis = !assigned || detail?.plan.status === 'AWAITING_SALES_DATA' || detail?.plan.status === 'SALES_REVISION'
  const fixed = plan && (assigned || initialDates.periodStart !== plan.periodStart || initialDates.periodEnd !== plan.periodEnd)
  const [period, setPeriod] = useState(selection)
  const [regionId, setRegionId] = useState(plan ? String(plan.regionId) : '')
  const [managerId, setManagerId] = useState(plan?.manager ? String(plan.manager.id) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const managers = options.data?.find(region => String(region.id) === regionId)?.managers ?? []
  const existingManager = plan?.manager
  const managerMissing = existingManager && String(existingManager.id) === managerId
    && !managers.some(manager => String(manager.id) === managerId)
  const ready = !!options.data && !options.error

  function formatAmount(event: FocusEvent<HTMLInputElement>) {
    const amount = normalizeMoney(event.currentTarget.value, 16)
    if (amount !== null) event.currentTarget.value = formatPlanMoney(amount)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || !ready) return
    const requestAnalysis = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'analysis'
    const form = new FormData(event.currentTarget)
    const goal = normalizeMoney(String(form.get('targetRevenue')), 16)
    const budgetText = String(form.get('budgetLimit')).trim()
    const discountText = String(form.get('maxDiscountPercent')).trim()
    const budget = budgetText ? normalizeMoney(budgetText, 16) : null
    const discount = discountText ? normalizeDecimal(discountText, 3, 2) : null
    const issues: Record<string, string> = {}
    if (goal === null || Number(goal) <= 0) issues.targetRevenue = 'Введите положительную сумму с точностью до копейки'
    if (budgetText && budget === null) issues.budgetLimit = 'Введите сумму с точностью до копейки'
    if (discountText && (discount === null || Number(discount) > 100)) issues.maxDiscountPercent = 'Введите процент от 0 до 100'
    if ((requestAnalysis || assigned) && !managerId) issues.managerId = 'Выберите ответственного менеджера'
    setFields(issues)
    setError('')
    if (Object.keys(issues).length) return

    const dates = fixed ? { periodStart: plan.periodStart, periodEnd: plan.periodEnd } : periodDates(period)
    setBusy(true)
    try {
      const saved = await api<PlanSummary>(plan ? '/plans/' + plan.id : '/plans', {
        method: plan ? 'PUT' : 'POST',
        body: JSON.stringify({
          ...dates,
          regionId: Number(regionId),
          managerId: managerId ? Number(managerId) : null,
          targetRevenue: goal,
          budgetLimit: budget,
          maxDiscountPercent: discount,
          planningComment: form.get('planningComment'),
          requestAnalysis,
          version: plan?.version,
        }),
      })
      onSaved(saved)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить план.')
      if (cause instanceof ApiError) setFields(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title={plan ? 'Параметры задания' : 'Новый региональный план'} busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {options.error && <div className="alert alert-error" role="alert">
          {options.error}<button type="button" className="text-button" onClick={() => void options.refresh()}>Повторить</button>
        </div>}
        {options.loading && !options.data && <Loading label="Загружаем регионы и назначения…"/>}
        {options.data && <fieldset className="admin-form-grid" disabled={busy || !ready}>
          <label className="admin-field">
            Регион
            {fixed ? <input value={plan.regionName} readOnly/> : (
              <select name="regionId" value={regionId} required aria-invalid={!!fields.regionId} onChange={event => {
                setRegionId(event.target.value)
                setManagerId('')
              }}>
                <option value="">Выберите регион</option>
                {options.data.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
              </select>
            )}
            {fields.regionId && <small className="field-error">{fields.regionId}</small>}
          </label>
          <label className="admin-field">
            Ответственный менеджер
            <select name="managerId" value={managerId} onChange={event => setManagerId(event.target.value)}
              aria-invalid={!!fields.managerId} disabled={!regionId}>
              <option value="">Выберите менеджера</option>
              {managerMissing && <option value={existingManager.id}>{existingManager.name} (назначение завершено)</option>}
              {managers.map(manager => <option key={manager.id} value={manager.id}>{manager.name}</option>)}
            </select>
            {fields.managerId && <small className="field-error">{fields.managerId}</small>}
            {regionId && !managers.length && <small className="plan-field-hint">Для отправки задания нужно назначение на этот регион.</small>}
          </label>
          {fixed ? (
            <label className="admin-field admin-field-wide">
              Плановый период
              <input value={formatPeriod(plan.periodStart, plan.periodEnd)} readOnly/>
            </label>
          ) : <CalendarPeriodFields value={period} onChange={setPeriod} error={fields.periodStart || fields.periodEnd}/>}
          <label className="admin-field">
            Целевая выручка, BYN
            <input name="targetRevenue" inputMode="decimal" defaultValue={plan?.targetRevenue ? formatPlanMoney(plan.targetRevenue) : ''}
              required onBlur={formatAmount} onChange={() => setFields(previous => ({ ...previous, targetRevenue: '' }))}
              aria-invalid={!!fields.targetRevenue}/>
            {fields.targetRevenue && <small className="field-error">{fields.targetRevenue}</small>}
          </label>
          <label className="admin-field">
            Лимит затрат, BYN
            <input name="budgetLimit" inputMode="decimal" defaultValue={detail?.budgetLimit ? formatPlanMoney(detail.budgetLimit) : ''}
              onBlur={formatAmount} onChange={() => setFields(previous => ({ ...previous, budgetLimit: '' }))}
              aria-invalid={!!fields.budgetLimit}/>
            {fields.budgetLimit && <small className="field-error">{fields.budgetLimit}</small>}
          </label>
          <label className="admin-field">
            Максимальная скидка, %
            <input name="maxDiscountPercent" inputMode="decimal" maxLength={6} defaultValue={detail?.maxDiscountPercent ?? ''}
              aria-invalid={!!fields.maxDiscountPercent}/>
            {fields.maxDiscountPercent && <small className="field-error">{fields.maxDiscountPercent}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Комментарий к заданию
            <textarea name="planningComment" className="product-description" rows={3} maxLength={2000}
              defaultValue={detail?.planningComment ?? ''}/>
            {fields.planningComment && <small className="field-error">{fields.planningComment}</small>}
          </label>
        </fieldset>}
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className={'button ' + (assigned ? 'button-primary' : 'button-secondary')} disabled={busy || !ready}>
            {assigned ? 'Сохранить изменения' : 'Сохранить черновик'}
          </button>
          {canRequestAnalysis && <button type="submit" value="analysis" className="button button-primary" disabled={busy || !ready}>
            Передать на анализ
          </button>}
        </footer>
      </form>
    </AdminDialog>
  )
}
