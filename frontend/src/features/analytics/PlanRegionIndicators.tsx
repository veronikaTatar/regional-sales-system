import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Loading } from '../../components/Loading'
import { useResource } from '../../hooks/useResource'
import { formatPeriod } from '../plans/planFormat'
import { AnalysisSource, indicatorFormat } from './AnalysisResults'
import { MetricBars } from './MetricBars'
import type { CpiMonth, SalaryMonth, Statistics, TurnoverMonth } from './types'

const series = [
  { endpoint: 'non-food-cpi', name: 'Изменение цен на непродовольственные товары, %' },
  { endpoint: 'salaries', name: 'Средняя зарплата, BYN в месяц' },
  { endpoint: 'turnover', name: 'Розничный товарооборот с начала года, BYN' },
]

const monthLabel = new Intl.DateTimeFormat('ru-BY', { month: 'short', year: 'numeric', timeZone: 'UTC' })

export function PlanRegionIndicators({ regionId, regionName, through }: { regionId: number; regionName: string; through: string }) {
  const [metric, setMetric] = useState(0)
  const option = series[metric]
  const annual = useResource<Statistics | undefined>('/analytics/statistics/latest?through=' + through)
  const monthly = useResource<(CpiMonth | SalaryMonth | TurnoverMonth)[]>('/analytics/statistics/' + option.endpoint)
  const region = annual.data?.regions.find(row => row.regionId === regionId)
  const chart = (monthly.data ?? []).filter(month => month.period <= through.slice(0, 7)).slice().reverse().flatMap(month => {
    const row = month.regions.find(row => row.regionName === regionName)
    if (!row) return []
    const value = 'changePercent' in row ? row.changePercent : 'averageSalary' in row ? row.averageSalary : row.cumulativeTurnover
    return [{ label: monthLabel.format(new Date(month.period + '-01T00:00:00Z')), value, display: indicatorFormat.format(value) }]
  })

  return <section className="analysis-section" aria-label="Показатели региона">
    <div className="panel plan-parameters">
      <div className="plan-parameters-heading"><h2>Показатели региона</h2><Link to="/statistics" className="text-button">Внешние данные</Link></div>
      {annual.error ? <p className="alert alert-error" role="alert">{annual.error} <button type="button" className="text-button" onClick={() => void annual.refresh()}>Повторить</button></p>
        : annual.loading ? <Loading label="Загружаем годовые показатели…"/>
          : region && annual.data ? <>
            <p className="analysis-inline-note"><AnalysisSource source={annual.data.source} periodStart={annual.data.periodStart}/> {formatPeriod(annual.data.periodStart, annual.data.periodEnd)}</p>
            <dl><div><dt>Средняя зарплата</dt><dd>{indicatorFormat.format(region.averageSalary)} BYN в месяц</dd></div>
              <div><dt>Товарооборот на жителя</dt><dd>{indicatorFormat.format(region.retailPerCapita)} BYN</dd></div>
              <div><dt>Численность населения</dt><dd>{indicatorFormat.format(region.population)} чел.</dd></div></dl>
          </> : <p className="analysis-inline-note">Годовые показатели для периода плана ещё не загружены.</p>}
      <label className="admin-field region-series-select">Показатель Белстата по месяцам
        <select value={metric} onChange={event => setMetric(Number(event.target.value))}>
          {series.map((entry, index) => <option key={entry.endpoint} value={index}>{entry.name}</option>)}
        </select>
      </label>
      {monthly.error ? <p className="alert alert-error" role="alert">{monthly.error} <button type="button" className="text-button" onClick={() => void monthly.refresh()}>Повторить</button></p>
        : monthly.loading ? <Loading label="Загружаем месячные показатели…"/>
          : !chart.length && <p className="analysis-inline-note">До начала плана выпусков по показателю для этого региона нет.</p>}
    </div>
    {!monthly.loading && !monthly.error && <MetricBars title={option.name} rows={chart}/>}
  </section>
}
