import { BelstatMonthlyPanel } from './BelstatMonthlyPanel'
import type { CpiMonth } from './types'

const indexFormat = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 4 })
const changeFormat = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 4, signDisplay: 'exceptZero' })

export function BelstatCpi() {
  return <BelstatMonthlyPanel<CpiMonth> id="belstat-cpi" title="ИПЦ на непродовольственные товары"
    endpoint="/analytics/statistics/non-food-cpi" emptyMessage="Региональные выпуски ИПЦ ещё не загружены.">
    {month => <>
      <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr>
            <th scope="col">Регион</th>
            <th scope="col" className="numeric">ИПЦ к предыдущему месяцу, %</th>
            <th scope="col" className="numeric">Изменение цен за месяц, %</th>
          </tr></thead>
          <tbody>{month.regions.map(region => <tr key={region.regionCode}>
            <th scope="row">{region.regionName}</th>
            <td className="numeric">{indexFormat.format(region.indexPercent)}</td>
            <td className="numeric">{changeFormat.format(region.changePercent)}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <p className="analysis-method-note analysis-inline-note">Индекс 100 % означает, что цены в среднем остались на уровне предыдущего месяца.</p>
    </>}
  </BelstatMonthlyPanel>
}
