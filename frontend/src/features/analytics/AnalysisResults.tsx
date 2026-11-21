import { Link } from 'react-router-dom'
import type { Calculation, Recommendation, StatisticsSource } from './types'

export const indexFormat = new Intl.NumberFormat('ru-BY', { minimumFractionDigits: 3, maximumFractionDigits: 6 })
export const indicatorFormat = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 2 })
export const publicationDate = new Intl.DateTimeFormat('ru-BY', { timeZone: 'Europe/Minsk', dateStyle: 'medium' })
const signedPercent = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 2, signDisplay: 'exceptZero' })

export function AnalysisSource({ source, periodStart }: { source: StatisticsSource; periodStart?: string }) {
  return <span className="mini-status analysis-source-label">{source === 'TRAINING' ? 'Учебные данные'
    : periodStart ? 'Белстат · ' + periodStart.slice(0, 4) : 'Белстат'}</span>
}

export function AnalysisMetrics({ index, share, adjustment }: { index: number; share: number; adjustment: number }) {
  return <dl className="analysis-metrics">
    <div><dt>Индекс покупательной способности</dt><dd>{indexFormat.format(index)}</dd></div>
    <div><dt>Рекомендуемая доля региона</dt><dd>{indicatorFormat.format(share)} %</dd></div>
    <div><dt>Ценовой ориентир к прайсу</dt><dd>{signedPercent.format(adjustment)} %</dd></div>
  </dl>
}

export function AnalysisRanking({ calculation, selectedRegionId }: { calculation: Calculation; selectedRegionId?: number }) {
  return <section className="panel catalog-panel analysis-section" aria-label="Сравнение регионов">
    <div className="plan-section-top">
      <h2>Сравнение регионов</h2>
      <AnalysisSource source={calculation.source} periodStart={calculation.periodStart}/>
    </div>
    <div className="table-scroll">
      <table className="product-table plan-list-table analysis-ranking">
        <thead><tr>
          <th scope="col">Регион</th>
          <th scope="col" className="numeric">Средняя зарплата, BYN</th>
          <th scope="col" className="numeric">Оборот на жителя за период, BYN</th>
          <th scope="col" className="numeric">Население, чел.</th>
          <th scope="col">Индекс от 0 до 1</th>
          <th scope="col" className="numeric">Доля, %</th>
        </tr></thead>
        <tbody>{calculation.ranking.map(({ indicators, index, sharePercent }) => (
          <tr key={indicators.regionId} className={selectedRegionId === indicators.regionId ? 'analysis-selected-region' : undefined}>
            <th scope="row">{indicators.regionName}</th>
            <td className="numeric">{indicatorFormat.format(indicators.averageSalary)}</td>
            <td className="numeric">{indicatorFormat.format(indicators.retailPerCapita)}</td>
            <td className="numeric">{indicatorFormat.format(indicators.population)}</td>
            <td className="analysis-index-cell">
              <span className="analysis-index-track" aria-hidden="true"><span style={{ width: index * 100 + '%' }}/></span>
              {indexFormat.format(index)}
            </td>
            <td className="numeric">{indicatorFormat.format(sharePercent)}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
    <p className="analysis-method-note">Веса в процентах · зарплата {indicatorFormat.format(calculation.weights.salary)} · оборот на жителя {indicatorFormat.format(calculation.weights.retail)} · население {indicatorFormat.format(calculation.weights.population)}<br/>
      Средний индекс {indexFormat.format(calculation.averageIndex)}.
      {calculation.refinancingRate != null && <> Ставка рефинансирования {indicatorFormat.format(calculation.refinancingRate)} % приведена для справки.</>}</p>
  </section>
}

export function PlanRecommendation({ recommendation, linkToCalculation = false }: {
  recommendation: Recommendation
  linkToCalculation?: boolean
}) {
  const { publication, sharePercent, priceAdjustmentPercent } = recommendation
  return <section className="panel plan-parameters analysis-section" aria-label="Рекомендация аналитика">
    <div className="plan-parameters-heading"><h2>Рекомендация аналитика</h2><AnalysisSource source={publication.source}/></div>
    <p className="analysis-author">{publication.author} · {publicationDate.format(new Date(publication.publishedAt))}</p>
    <AnalysisMetrics index={publication.index} share={sharePercent} adjustment={priceAdjustmentPercent}/>
    <p className="plan-comment">{publication.text}</p>
    {linkToCalculation && <Link className="text-button analysis-detail-link" to={'/analytics?plan=' + publication.planId}>Открыть сравнение регионов</Link>}
  </section>
}
