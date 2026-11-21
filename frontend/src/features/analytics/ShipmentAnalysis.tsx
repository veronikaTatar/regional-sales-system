import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Loading } from '../../components/Loading'
import { formatCents, formatPeriod, formatPlanQuantity, planCents } from '../plans/planFormat'
import type { ShipmentOptions } from '../shipments/types'
import { useResource } from '../../hooks/useResource'
import { MetricBars } from './MetricBars'

interface MonthlyRow {
  month: string
  regionId: number
  productId: number
  shipmentCount: number
  quantity: string
  revenue: string
}

interface SummaryPeriod {
  from: string | null
  to: string | null
  rows: MonthlyRow[]
}

interface ShipmentSummary { current: SummaryPeriod; previous: SummaryPeriod | null }

const monthLabel = new Intl.DateTimeFormat('ru-BY', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const integer = new Intl.NumberFormat('ru-BY')
const dateText = (date: Date) => date.toISOString().slice(0, 10)

function groupTotals<K>(rows: MonthlyRow[], key: (row: MonthlyRow) => K) {
  const totals = new Map<K, { quantity: bigint; revenue: bigint; count: number }>()
  for (const row of rows) {
    const id = key(row)
    const total = totals.get(id) ?? { quantity: 0n, revenue: 0n, count: 0 }
    const [whole, fraction = ''] = row.quantity.split('.')
    total.quantity += BigInt(whole + fraction.padEnd(3, '0'))
    total.revenue += planCents(row.revenue)
    total.count += row.shipmentCount
    totals.set(id, total)
  }
  return totals
}

function quantityText(value: bigint) {
  const absolute = value < 0n ? -value : value
  return (value < 0n ? '−' : '') + formatPlanQuantity(`${absolute / 1000n}.${String(absolute % 1000n).padStart(3, '0')}`)
}

function monthRange(month: string, period: SummaryPeriod) {
  const start = new Date(month + '-01T00:00:00Z')
  const end = new Date(start)
  end.setUTCMonth(end.getUTCMonth() + 1, 0)
  const from = dateText(start)
  const to = dateText(end)
  return { from: period.from && period.from > from ? period.from : from,
    to: period.to && period.to < to ? period.to : to }
}

export function ShipmentAnalysis({ region }: {
  region?: { id: number; name: string; from: string; to: string }
}) {
  const initial = { from: region?.from ?? '', to: region?.to ?? '', regionId: String(region?.id ?? ''), productId: '' }
  const [filters, setFilters] = useState(initial)
  const [applied, setApplied] = useState(initial)
  const params = new URLSearchParams(Object.entries(applied).filter(([, value]) => value))
  const options = useResource<ShipmentOptions>('/shipments/options')
  const summary = useResource<ShipmentSummary>('/shipments/summary?' + params)
  const invalidPeriod = Boolean(filters.from && filters.to && filters.from > filters.to)
  const error = options.error || summary.error

  return <section aria-label={region ? 'Отгрузки региона' : 'Анализ отгрузок'}>
    <form className="panel plan-parameters analysis-section shipment-analysis-filters" onSubmit={event => {
      event.preventDefault()
      if (!invalidPeriod) setApplied({ ...filters, to: filters.to || region?.to || '' })
    }}>
      <h2>{region ? 'Отгрузки региона' : 'Фильтры отгрузок'}</h2>
      <div className="shipment-analysis-fields">
        {!region && <label className="admin-field">Регион
          <select value={filters.regionId} onChange={event => setFilters({ ...filters, regionId: event.target.value })}>
            <option value="">Все регионы</option>
            {options.data?.regions.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>}
        <label className="admin-field">Товар или артикул
          <select value={filters.productId} onChange={event => setFilters({ ...filters, productId: event.target.value })}>
            <option value="">Все товары</option>
            {options.data?.products.map(item => <option key={item.id} value={item.id}>{item.sku} · {item.name}</option>)}
          </select>
        </label>
        <label className="admin-field">Дата с
          <input type="date" value={filters.from} max={region?.to} required={Boolean(region)}
            onChange={event => setFilters({ ...filters, from: event.target.value })}/>
        </label>
        <label className="admin-field">Дата по
          <input type="date" value={filters.to} max={region?.to} required={Boolean(region)}
            onChange={event => setFilters({ ...filters, to: event.target.value })}/>
        </label>
        <button className="button button-primary" type="submit" disabled={invalidPeriod}>Показать</button>
      </div>
      {invalidPeriod && <p className="alert alert-error" role="alert">Дата окончания должна быть не раньше даты начала.</p>}
    </form>
    {error ? <div className="alert alert-error" role="alert">{error} <button type="button" className="text-button"
      onClick={() => void (options.error ? options.refresh() : summary.refresh())}>Повторить</button></div>
      : options.loading || summary.loading ? <Loading label="Сводим отгрузки…"/>
        : options.data && summary.data && <ShipmentSummaryView summary={summary.data} options={options.data} params={params} regional={Boolean(region)}/>}
  </section>
}

function ShipmentSummaryView({ summary, options, params, regional }: {
  summary: ShipmentSummary; options: ShipmentOptions; params: URLSearchParams; regional: boolean
}) {
  const { current, previous } = summary
  const period = current.from && current.to ? formatPeriod(current.from, current.to)
    : current.from ? 'С ' + current.from : current.to ? 'По ' + current.to : 'За всё время'
  const products = groupTotals(current.rows, row => row.productId)
  const earlier = groupTotals(previous?.rows ?? [], row => row.productId)
  const totals = groupTotals(current.rows, () => 0).get(0)
  const months = [...groupTotals(current.rows, row => row.month)].sort(([a], [b]) => a.localeCompare(b))
  const byRegion = groupTotals(current.rows, row => row.regionId)
  const journalLink = (overrides: Record<string, string> = {}) => {
    const query = new URLSearchParams(params)
    for (const [key, value] of Object.entries(overrides)) query.set(key, value)
    return '/shipments?' + query
  }
  const listing = options.products.filter(row => products.has(row.id) || earlier.has(row.id))
    .sort((a, b) => Number((products.get(b.id)?.revenue ?? 0n) - (products.get(a.id)?.revenue ?? 0n)))

  return <>
    <section className="panel plan-parameters analysis-section">
      <div className="plan-parameters-heading"><h2>Итоги отгрузок</h2><Link to={journalLink()} className="text-button">Открыть журнал</Link></div>
      <p className="analysis-inline-note">{period}{previous?.from && previous.to && ' · Сравнение с ' + formatPeriod(previous.from, previous.to)}</p>
      <dl><div><dt>Сумма без НДС</dt><dd>{formatCents(totals?.revenue ?? 0n)} BYN</dd></div>
        <div><dt>Строки отгрузок</dt><dd>{integer.format(totals?.count ?? 0)}</dd></div></dl>
      {!totals && <p className="analysis-inline-note">Отгрузок за выбранный период нет.</p>}
    </section>
    <div className="shipment-charts">
      <MetricBars title="Сумма отгрузок по месяцам, BYN" rows={months.map(([month, total]) => ({
        label: monthLabel.format(new Date(month + '-01T00:00:00Z')), value: Number(total.revenue),
        display: formatCents(total.revenue), to: journalLink(monthRange(month, current)),
      }))}/>
      {!regional && <MetricBars title="Сумма отгрузок по регионам, BYN" rows={options.regions.filter(row => byRegion.has(row.id)).map(row => {
        const revenue = byRegion.get(row.id)?.revenue ?? 0n
        return { label: row.name, value: Number(revenue), display: formatCents(revenue), to: journalLink({ regionId: String(row.id) }) }
      })}/>}
    </div>
    {listing.length > 0 && <section className="panel catalog-panel analysis-section" aria-label="Отгрузки по товарам">
      <div className="plan-section-top"><h2>Отгрузки по товарам</h2></div>
      <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr><th scope="col">Товар и артикул</th><th scope="col" className="numeric">Количество</th>
            <th scope="col" className="numeric">Сумма, BYN</th>
            {previous && <><th scope="col" className="numeric">Количество ранее</th>
              <th scope="col" className="numeric">Изменение количества</th><th scope="col" className="numeric">Изменение суммы, BYN</th></>}
          </tr></thead>
          <tbody>{listing.map(item => {
            const quantity = products.get(item.id)?.quantity ?? 0n
            const revenue = products.get(item.id)?.revenue ?? 0n
            const previousQuantity = earlier.get(item.id)?.quantity ?? 0n
            const revenueDelta = revenue - (earlier.get(item.id)?.revenue ?? 0n)
            const quantityDelta = quantity - previousQuantity
            return <tr key={item.id}><th scope="row"><Link to={journalLink({ productId: String(item.id) })} className="text-button">
              {item.name}</Link><small className="cell-secondary">{item.sku}</small></th>
              <td className="numeric">{quantityText(quantity)} {item.unit}</td><td className="numeric">{formatCents(revenue)}</td>
              {previous && <><td className="numeric">{quantityText(previousQuantity)} {item.unit}</td>
                <td className="numeric">{quantityDelta > 0n && '+'}{quantityText(quantityDelta)} {item.unit}</td>
                <td className="numeric">{revenueDelta > 0n && '+'}{formatCents(revenueDelta)}</td></>}
            </tr>
          })}</tbody>
        </table>
      </div>
    </section>}
  </>
}
