import { useState } from 'react'
import { CalendarPeriodFields, initialPeriod, periodDates } from '../components/CalendarPeriodFields'
import { Loading } from '../components/Loading'
import { TrainingSource, indicatorFormat } from '../features/analytics/AnalysisResults'
import type { Statistics } from '../features/analytics/types'
import { formatPeriod } from '../features/plans/planFormat'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function StatisticsPage() {
  const [period, setPeriod] = useState(() => initialPeriod())
  const [dates, setDates] = useState(() => periodDates(period))
  const path = '/analytics/statistics?' + new URLSearchParams(dates).toString()
  const statistics = useResource<Statistics>(path)
  const data = statistics.data

  return <>
    <PageHeading eyebrow="АНАЛИТИКА" title="Статистика" subtitle="Региональные показатели для расчёта покупательной способности.">
      <TrainingSource/>
    </PageHeading>
    <form className="panel plan-parameters analysis-period-form analysis-section" onSubmit={event => {
      event.preventDefault()
      const next = periodDates(period)
      if (dates.periodStart === next.periodStart && dates.periodEnd === next.periodEnd) void statistics.refresh()
      else setDates(next)
    }}>
      <fieldset className="admin-form-grid" disabled={statistics.loading}>
        <CalendarPeriodFields value={period} onChange={setPeriod}/>
      </fieldset>
      <button type="submit" className="button button-primary" disabled={statistics.loading}>Показать показатели</button>
    </form>
    {statistics.error && <div className="alert alert-error" role="alert">{statistics.error}
      <button type="button" className="text-button" onClick={() => void statistics.refresh()}>Повторить</button>
    </div>}
    {statistics.loading && <Loading label="Загружаем показатели…"/>}
    {data && !statistics.error && !statistics.loading && <>
      <section className="panel catalog-panel analysis-section" aria-label="Региональные показатели">
        <div className="plan-section-top"><h2>Белстат · учебные показатели</h2><span>{formatPeriod(data.periodStart, data.periodEnd)}</span></div>
        {data.regions.length ? <div className="table-scroll">
          <table className="product-table plan-list-table analysis-ranking">
            <thead><tr>
              <th scope="col">Регион</th>
              <th scope="col" className="numeric">Средняя зарплата, BYN в месяц</th>
              <th scope="col" className="numeric">Доход на жителя, BYN в месяц</th>
              <th scope="col" className="numeric">Оборот на жителя за период, BYN</th>
              <th scope="col" className="numeric">Население, чел.</th>
            </tr></thead>
            <tbody>{data.regions.map(row => <tr key={row.regionId}>
              <th scope="row">{row.regionName}</th>
              <td className="numeric">{indicatorFormat.format(row.averageSalary)}</td>
              <td className="numeric">{indicatorFormat.format(row.incomePerCapita)}</td>
              <td className="numeric">{indicatorFormat.format(row.retailPerCapita)}</td>
              <td className="numeric">{indicatorFormat.format(row.population)}</td>
            </tr>)}</tbody>
          </table>
        </div> : <p className="plan-empty-inline">За выбранный период нет региональных показателей.</p>}
      </section>
      <section className="panel plan-parameters analysis-section" aria-labelledby="national-statistics-heading">
        <div className="plan-parameters-heading"><h2 id="national-statistics-heading">Нацбанк · учебный показатель</h2></div>
        <dl><div><dt>Ставка рефинансирования по Беларуси</dt><dd>{indicatorFormat.format(data.refinancingRate)} %</dd></div></dl>
        <p className="analysis-method-note analysis-inline-note">Ставка приведена для справки. Региональный индекс использует зарплату, оборот на жителя и население.</p>
      </section>
    </>}
  </>
}
