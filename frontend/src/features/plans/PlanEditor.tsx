import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { Loading } from '../../components/Loading'
import { useResource } from '../../hooks/useResource'
import { AdminDialog } from '../administration/AdminDialog'
import { formatPeriod, normalizeDecimal } from './planFormat'
import type { PlanDetail, PlanRegion, PlanSummary } from './types'

type PeriodKind = 'MONTH' | 'QUARTER'
const minskDate = new Intl.DateTimeFormat('sv-SE', { dateStyle: 'short', timeZone: 'Europe/Minsk' })
const months = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
const quarters = ['I квартал', 'II квартал', 'III квартал', 'IV квартал']

function periodDates(year: number, kind: PeriodKind, number: number) {
  const startMonth = kind === 'MONTH' ? number : (number - 1) * 3 + 1
  const endMonth = kind === 'MONTH' ? startMonth : startMonth + 2
  const monthText = (month: number) => String(month).padStart(2, '0')
  const lastDay = new Date(Date.UTC(year, endMonth, 0)).getUTCDate()
  return {
    periodStart: year + '-' + monthText(startMonth) + '-01',
    periodEnd: year + '-' + monthText(endMonth) + '-' + lastDay,
  }
}

export function PlanEditor({ detail, onClose, onSaved }: {
  detail?: PlanDetail
  onClose: () => void
  onSaved: (plan: PlanSummary) => void
}) {
  const plan = detail?.plan
  const options = useResource<PlanRegion[]>('/plans/options')
  const [initialYear, initialMonth] = (plan?.periodStart ?? minskDate.format(new Date())).split('-').map(Number)
  const initialKind = plan && plan.periodStart.slice(0, 7) === plan.periodEnd.slice(0, 7) ? 'MONTH' : 'QUARTER'
  const initialPeriod = initialKind === 'MONTH' ? initialMonth : Math.ceil(initialMonth / 3)
  const initialDates = periodDates(initialYear, initialKind, initialPeriod)
  const fixed = plan && (plan.sentToManagerAt || initialDates.periodStart !== plan.periodStart || initialDates.periodEnd !== plan.periodEnd)
  const [year, setYear] = useState(String(initialYear))
  const [kind, setKind] = useState<PeriodKind>(initialKind)
  const [period, setPeriod] = useState(initialPeriod)
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

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || !ready) return
    const sendToManager = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'dispatch'
    const form = new FormData(event.currentTarget)
    const goal = normalizeDecimal(String(form.get('targetRevenue')), 16, 2)
    const budgetText = String(form.get('budgetLimit')).trim()
    const discountText = String(form.get('maxDiscountPercent')).trim()
    const budget = budgetText ? normalizeDecimal(budgetText, 16, 2) : null
    const discount = discountText ? normalizeDecimal(discountText, 3, 2) : null
    const issues: Record<string, string> = {}
    if (goal === null || Number(goal) <= 0) issues.targetRevenue = 'Введите положительную сумму с точностью до копейки'
    if (budgetText && budget === null) issues.budgetLimit = 'Введите сумму с точностью до копейки'
    if (discountText && (discount === null || Number(discount) > 100)) issues.maxDiscountPercent = 'Введите процент от 0 до 100'
    if ((sendToManager || plan?.sentToManagerAt) && !managerId) issues.managerId = 'Выберите ответственного менеджера'
    setFields(issues)
    setError('')
    if (Object.keys(issues).length) return

    const dates = fixed ? { periodStart: plan.periodStart, periodEnd: plan.periodEnd } : periodDates(Number(year), kind, period)
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
          sendToManager,
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
    <AdminDialog title={plan ? 'Задание менеджеру' : 'Новый региональный план'} busy={busy} onClose={onClose}>
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
          ) : <>
            <label className="admin-field">
              Год
              <input name="year" type="number" min="1000" max="9999" step="1" value={year} required
                onChange={event => setYear(event.target.value)}/>
            </label>
            <label className="admin-field">
              Вид периода
              <select name="periodKind" value={kind} onChange={event => {
                setKind(event.target.value as PeriodKind)
                setPeriod(1)
              }}>
                <option value="QUARTER">Квартал</option>
                <option value="MONTH">Месяц</option>
              </select>
            </label>
            <label className="admin-field admin-field-wide">
              {kind === 'MONTH' ? 'Месяц' : 'Квартал'}
              <select name="period" value={period} onChange={event => setPeriod(Number(event.target.value))}>
                {(kind === 'MONTH' ? months : quarters).map((label, index) => <option key={label} value={index + 1}>{label}</option>)}
              </select>
              {(fields.periodStart || fields.periodEnd) && <small className="field-error">{fields.periodStart || fields.periodEnd}</small>}
            </label>
          </>}
          <label className="admin-field">
            Целевая выручка, BYN
            <input name="targetRevenue" inputMode="decimal" maxLength={19} defaultValue={plan?.targetRevenue ?? ''} required
              aria-invalid={!!fields.targetRevenue}/>
            {fields.targetRevenue && <small className="field-error">{fields.targetRevenue}</small>}
          </label>
          <label className="admin-field">
            Лимит затрат, BYN
            <input name="budgetLimit" inputMode="decimal" maxLength={19} defaultValue={detail?.budgetLimit ?? ''}
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
          <button type="submit" className={'button ' + (plan?.sentToManagerAt ? 'button-primary' : 'button-secondary')} disabled={busy || !ready}>
            {plan?.sentToManagerAt ? 'Сохранить изменения' : 'Сохранить черновик'}
          </button>
          {!plan?.sentToManagerAt && <button type="submit" value="dispatch" className="button button-primary" disabled={busy || !ready}>
            Передать менеджеру
          </button>}
        </footer>
      </form>
    </AdminDialog>
  )
}
