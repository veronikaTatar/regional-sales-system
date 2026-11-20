import { Link, useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { AnalysisRanking, indexFormat, PlanRecommendation, publicationDate, TrainingSource } from '../features/analytics/AnalysisResults'
import type { Publication, PublishedCalculation } from '../features/analytics/types'
import { formatPeriod } from '../features/plans/planFormat'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function AnalyticsPage({ history = false }: { history?: boolean }) {
  const publications = useResource<Publication[]>('/analytics/recommendations')
  const [params, setParams] = useSearchParams()
  const rows = publications.data ?? []
  const selected = rows.find(row => String(row.planId) === params.get('plan')) ?? rows[0]

  return <>
    <PageHeading eyebrow="АНАЛИТИКА" title={history ? 'Рекомендации' : 'Аналитика'}
      subtitle={history ? 'Выводы аналитиков, переданные в региональные планы.' : 'Сравнение регионов по сохранённым расчётам покупательной способности.'}/>
    {publications.error && <div className="alert alert-error" role="alert">{publications.error}
      <button type="button" className="text-button" onClick={() => void publications.refresh()}>Повторить</button>
    </div>}
    {publications.loading && <Loading label="Загружаем опубликованные расчёты…"/>}
    {publications.data && !publications.error && !rows.length && <section className="panel empty-state">
      <h2>Опубликованных рекомендаций пока нет</h2>
      <p>Расчёт появится здесь после передачи рекомендации менеджеру.</p>
    </section>}
    {history && rows.length > 0 && !publications.error && <section className="panel catalog-panel" aria-label="Журнал рекомендаций">
      <div className="plan-section-top"><h2>Журнал рекомендаций</h2><TrainingSource/></div>
      <div className="table-scroll">
        <table className="product-table plan-list-table analysis-rankings-history">
          <thead><tr><th scope="col">Регион и период</th><th scope="col">Рекомендация</th><th scope="col">Индекс</th><th scope="col">Действия</th></tr></thead>
          <tbody>{rows.map(row => <tr key={row.planId}>
            <td><strong>{row.regionName}</strong><small className="cell-secondary plan-nowrap">{formatPeriod(row.periodStart, row.periodEnd)}</small></td>
            <td className="analysis-history-text"><p>{row.text}</p><small className="cell-secondary">{row.author} · {publicationDate.format(new Date(row.publishedAt))}</small></td>
            <td>{indexFormat.format(row.index)}</td>
            <td><Link className="text-button" to={'/plans/' + row.planId}>К плану</Link></td>
          </tr>)}</tbody>
        </table>
      </div>
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

function PublishedAnalysis({ planId }: { planId: number }) {
  const { data, loading, error, refresh } = useResource<PublishedCalculation>('/analytics/recommendations/' + planId)
  if (error) return <div className="alert alert-error" role="alert">{error}
    <button type="button" className="text-button" onClick={() => void refresh()}>Повторить</button>
  </div>
  if (loading || !data) return <Loading label="Загружаем сравнение регионов…"/>
  return <>
    <PlanRecommendation recommendation={data.recommendation}/>
    <AnalysisRanking calculation={data.calculation}/>
    <Link className="text-button analysis-detail-link" to={'/plans/' + planId}>Открыть связанный план</Link>
  </>
}
