import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { AnalysisRanking, AnalysisSource, indexFormat, PlanRecommendation, publicationDate } from '../features/analytics/AnalysisResults'
import { periodKey, PlanPeriodSelect } from '../features/analytics/PlanPeriodSelect'
import type { Publication, PublishedCalculation } from '../features/analytics/types'
import { formatPeriod } from '../features/plans/planFormat'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function AnalyticsPage({ history = false, embedded = false }: { history?: boolean; embedded?: boolean }) {
  const publications = useResource<Publication[]>('/analytics/recommendations')
  const [params, setParams] = useSearchParams()
  const rows = publications.data ?? []
  const [region, setRegion] = useState('')
  const [period, setPeriod] = useState('')
  const visible = rows.filter(row => (!region || row.regionName === region) && (!period || periodKey(row) === period))
  const selected = rows.find(row => String(row.planId) === params.get('plan')) ?? rows[0]

  return <>
    {!embedded && <PageHeading eyebrow="АНАЛИТИКА" title={history ? 'Рекомендации' : 'Аналитика'}
      subtitle={history ? 'Выводы аналитиков, переданные в региональные планы.' : 'Сравнение регионов по сохранённым расчётам покупательной способности.'}/>}
    {publications.error && <div className="alert alert-error" role="alert">{publications.error}
      <button type="button" className="text-button" onClick={() => void publications.refresh()}>Повторить</button>
    </div>}
    {publications.loading && <Loading label="Загружаем опубликованные расчёты…"/>}
    {publications.data && !publications.error && !rows.length && <section className="panel empty-state">
      <h2>Опубликованных рекомендаций пока нет</h2>
      <p>Расчёт появится здесь после передачи рекомендации менеджеру.</p>
    </section>}
    {history && rows.length > 0 && !publications.error && <section className="panel catalog-panel" aria-label="Журнал рекомендаций">
      <div className="plan-section-top"><h2>Переданные рекомендации</h2>
        {embedded && <div className="analysis-list-filters">
          <label className="admin-field">Регион<select value={region} onChange={event => setRegion(event.target.value)}>
            <option value="">Все регионы</option>
            {[...new Set(rows.map(row => row.regionName))].sort().map(name => <option key={name}>{name}</option>)}
          </select></label>
          <PlanPeriodSelect rows={rows} value={period} onChange={setPeriod}/>
        </div>}
      </div>
      {visible.length ? <div className="table-scroll">
        <table className="product-table plan-list-table analysis-rankings-history">
          <thead><tr><th scope="col">Регион и период</th><th scope="col">Рекомендация</th><th scope="col">Индекс</th><th scope="col">Действия</th></tr></thead>
          <tbody>{visible.map(row => <tr key={row.planId}>
            <td><strong>{row.regionName}</strong><small className="cell-secondary plan-nowrap">{formatPeriod(row.periodStart, row.periodEnd)}</small></td>
            <td className="analysis-history-text"><p>{row.text}</p><small className="cell-secondary">{row.author} · {publicationDate.format(new Date(row.publishedAt))}</small></td>
            <td>{indexFormat.format(row.index)}<small className="cell-secondary"><AnalysisSource source={row.source}/></small></td>
            <td><Link className="text-button" to={'/plans/' + row.planId + (embedded ? '?tab=analysis' : '')}>К плану</Link></td>
          </tr>)}</tbody>
        </table>
      </div> : <p className="plan-empty-inline">По выбранным фильтрам рекомендаций нет.</p>}
    </section>}
    {!history && selected && !publications.error && <>
      <section className="panel plan-parameters analysis-section">
        <label className="admin-field analysis-scenario-picker">
          Опубликованный расчёт
          <select value={selected.planId} onChange={event => setParams({ plan: event.target.value })}>
            {rows.map(row => <option key={row.planId} value={row.planId}>
              {row.regionName} · {formatPeriod(row.periodStart, row.periodEnd)}
            </option>)}
          </select>
        </label>
      </section>
      <PublishedAnalysis key={selected.planId} planId={selected.planId}/>
    </>}
  </>
}

export function PublishedAnalysis({ planId, inPlan = false }: { planId: number; inPlan?: boolean }) {
  const { data, loading, error, refresh } = useResource<PublishedCalculation>('/analytics/recommendations/' + planId)
  if (error) return <div className="alert alert-error" role="alert">{error}
    <button type="button" className="text-button" onClick={() => void refresh()}>Повторить</button>
  </div>
  if (loading || !data) return <Loading label="Загружаем сравнение регионов…"/>
  return <>
    <PlanRecommendation recommendation={data.recommendation}/>
    <AnalysisRanking calculation={data.calculation}/>
    {!inPlan && <Link className="text-button analysis-detail-link" to={'/plans/' + planId}>Открыть связанный план</Link>}
  </>
}
