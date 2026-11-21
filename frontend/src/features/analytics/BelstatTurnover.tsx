import { indicatorFormat } from './AnalysisResults'
import { BelstatMonthlyPanel } from './BelstatMonthlyPanel'
import type { TurnoverMonth } from './types'

export function BelstatTurnover() {
  return <BelstatMonthlyPanel<TurnoverMonth> id="belstat-turnover" title="Розничный товарооборот Белстата"
    endpoint="/analytics/statistics/turnover" emptyMessage="Месячные сводки оборота ещё не загружены.">
    {month => <>
      <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr>
            <th scope="col">Регион</th>
            <th scope="col" className="numeric">С начала года, BYN</th>
            <th scope="col" className="numeric">За месяц, BYN</th>
          </tr></thead>
          <tbody>{month.regions.map(region => <tr key={region.regionCode}>
            <th scope="row">{region.regionName}</th>
            <td className="numeric">{indicatorFormat.format(region.cumulativeTurnover)}</td>
            <td className="numeric">{region.monthlyTurnover == null ? '—' : indicatorFormat.format(region.monthlyTurnover)}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <p className="analysis-method-note analysis-inline-note">
        Оборот за месяц рассчитывается по разнице между соседними сводками. Для января используется итог январской сводки.
        {month.regions.some(region => region.monthlyTurnover == null) && ' Прочерк означает, что расчёт по сводкам недоступен.'}
      </p>
    </>}
  </BelstatMonthlyPanel>
}
