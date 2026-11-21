import { indicatorFormat } from './AnalysisResults'
import { BelstatMonthlyPanel } from './BelstatMonthlyPanel'
import type { SalaryMonth } from './types'

export function BelstatSalaries() {
  return <BelstatMonthlyPanel<SalaryMonth> id="belstat-salaries" title="Зарплаты Белстата по регионам"
    endpoint="/analytics/statistics/salaries" emptyMessage="Месячные зарплаты ещё не загружены.">
    {month => <div className="table-scroll">
      <table className="product-table plan-list-table">
        <thead><tr><th scope="col">Регион</th><th scope="col" className="numeric">Начисленная зарплата, BYN в месяц</th></tr></thead>
        <tbody>{month.regions.map(region => <tr key={region.regionCode}>
          <th scope="row">{region.regionName}</th>
          <td className="numeric">{indicatorFormat.format(region.averageSalary)}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </BelstatMonthlyPanel>
}
