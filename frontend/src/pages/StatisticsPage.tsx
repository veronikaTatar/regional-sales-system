import { useState } from 'react'
import { FileUp } from 'lucide-react'
import { Loading } from '../components/Loading'
import { AnalysisSource, indicatorFormat } from '../features/analytics/AnalysisResults'
import { BelstatSalaries } from '../features/analytics/BelstatSalaries'
import { BelstatImportDialog } from '../features/analytics/BelstatImportDialog'
import type { Statistics } from '../features/analytics/types'
import { formatPeriod } from '../features/plans/planFormat'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function StatisticsPage() {
  const latest = useResource<Statistics | undefined>('/analytics/statistics/latest')
  const [importing, setImporting] = useState(false)
  const [notice, setNotice] = useState('')

  return <>
    <PageHeading eyebrow="АНАЛИТИКА" title="Статистика" subtitle="Региональные показатели для расчёта покупательной способности.">
      <button type="button" className="button button-primary" onClick={() => setImporting(true)}>
        <FileUp size={16} aria-hidden="true"/>Импорт данных Белстата
      </button>
    </PageHeading>
    <BelstatSalaries/>
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {latest.error && <StatisticsError message={latest.error} onRetry={latest.refresh}/>}
    {latest.loading && <Loading label="Загружаем показатели…"/>}
    {latest.data && !latest.loading && !latest.error && <StatisticsTable data={latest.data}/>}
    {importing && <BelstatImportDialog onClose={() => setImporting(false)} onImported={result => {
      setImporting(false)
      setNotice('Загружены данные Белстата за ' + result.year + ' год. Регионов ' + result.regions + '.')
      void latest.refresh()
    }}/>}
  </>
}

function StatisticsError({ message, onRetry }: { message: string; onRetry: () => Promise<void> }) {
  return <div className="alert alert-error" role="alert">{message}
    <button type="button" className="text-button" onClick={() => void onRetry()}>Повторить</button>
  </div>
}

function StatisticsTable({ data }: { data: Statistics }) {
  const hasIncome = data.regions.some(row => row.incomePerCapita != null)
  return <section className="panel catalog-panel analysis-section" aria-label="Региональные показатели">
    <div className="plan-section-top">
      <h2>Региональные показатели</h2>
      <span>{formatPeriod(data.periodStart, data.periodEnd)}</span>
      <AnalysisSource source={data.source} periodStart={data.periodStart}/>
    </div>
    {data.regions.length ? <div className="table-scroll">
      <table className="product-table plan-list-table analysis-ranking">
        <thead><tr>
          <th scope="col">Регион</th>
          <th scope="col" className="numeric">Средняя зарплата, BYN в месяц</th>
          {hasIncome && <th scope="col" className="numeric">Доход на жителя, BYN в месяц</th>}
          <th scope="col" className="numeric">Оборот на жителя за период, BYN</th>
          <th scope="col" className="numeric">Население, чел.</th>
        </tr></thead>
        <tbody>{data.regions.map(row => <tr key={row.regionId}>
          <th scope="row">{row.regionName}</th>
          <td className="numeric">{indicatorFormat.format(row.averageSalary)}</td>
          {hasIncome && <td className="numeric">{row.incomePerCapita == null ? '—' : indicatorFormat.format(row.incomePerCapita)}</td>}
          <td className="numeric">{indicatorFormat.format(row.retailPerCapita)}</td>
          <td className="numeric">{indicatorFormat.format(row.population)}</td>
        </tr>)}</tbody>
      </table>
    </div> : <p className="plan-empty-inline">За выбранный период нет региональных показателей.</p>}
  </section>
}
