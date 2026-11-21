import { useState } from 'react'
import { Link } from 'react-router-dom'
import { periodKey, PlanPeriodSelect } from '../features/analytics/PlanPeriodSelect'
import { Loading } from '../components/Loading'
import { formatCents, formatGoalProgress, formatPeriod, formatPlanMoney, planCents } from '../features/plans/planFormat'
import type { PlanResult } from '../features/plans/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function ReportsPage({ embedded = false }: { embedded?: boolean }) {
  const results = useResource<PlanResult[]>('/plans/results')
  const [region, setRegion] = useState('')
  const [period, setPeriod] = useState('')
  const rows = results.data ?? []
  const regions = new Map(rows.map(({ plan }) => [plan.regionId, plan.regionName]))
  const visible = rows.filter(({ plan }) => (!region || String(plan.regionId) === region) && (!period || periodKey(plan) === period))

  return <>
    {!embedded && <PageHeading eyebrow="ПЛАНИРОВАНИЕ" title="Отчётность" subtitle="Утверждённые планы и фактическая выручка по отгрузкам за тот же период. Суммы в BYN без НДС."/>}
    {results.error && <div className="alert alert-error" role="alert">{results.error}
      <button type="button" className="text-button" onClick={() => void results.refresh()}>Повторить</button>
    </div>}
    {results.loading && <Loading label="Загружаем план и факт…"/>}
    {results.data && !results.error && <section className="panel catalog-panel" aria-label="План и факт продаж">
      <div className="plan-section-top">
        <h2>План и факт продаж</h2>
        <div className="analysis-list-filters">
        {regions.size > 1 && <label className="admin-field analysis-report-filter">
          Регион
          <select value={region} onChange={event => setRegion(event.target.value)}>
            <option value="">Все регионы</option>
            {[...regions].map(([id, name]) => <option key={id} value={id}>{name}</option>)}
          </select>
        </label>}
        {embedded && <PlanPeriodSelect rows={rows.map(row => row.plan)} value={period} onChange={setPeriod}/>}
        </div>
      </div>
      {visible.length ? <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr><th scope="col">Регион</th><th scope="col">Период</th><th scope="col" className="numeric">План, BYN</th>
            <th scope="col" className="numeric">Факт, BYN</th><th scope="col" className="numeric">Отклонение, BYN</th>
            <th scope="col" className="numeric">Выполнение</th><th scope="col">Действия</th></tr></thead>
          <tbody>{visible.map(({ plan, actualRevenue }) => {
            const actual = planCents(actualRevenue)
            return <tr key={plan.id}>
              <td><strong>{plan.regionName}</strong></td><td className="plan-nowrap">{formatPeriod(plan.periodStart, plan.periodEnd)}</td>
              <td className="numeric plan-nowrap">{formatPlanMoney(plan.plannedRevenue)}</td>
              <td className="numeric plan-nowrap">{formatPlanMoney(actualRevenue)}</td>
              <td className="numeric plan-nowrap">{formatCents(actual - planCents(plan.plannedRevenue))}</td>
              <td className="numeric plan-nowrap">{formatGoalProgress(actual, plan.plannedRevenue)}</td>
              <td><Link className="text-button" to={'/plans/' + plan.id + (embedded ? '?tab=results' : '')}>Открыть</Link></td>
            </tr>
          })}</tbody>
        </table>
      </div> : <div className="empty-state"><h2>{rows.length ? 'По выбранным фильтрам планов нет' : 'Утверждённых планов пока нет'}</h2></div>}
    </section>}
  </>
}
