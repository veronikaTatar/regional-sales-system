import { Link } from 'react-router-dom'

export function MetricBars({ title, rows }: {
  title: string
  rows: { label: string; value: number; display: string; to?: string }[]
}) {
  if (!rows.length) return null
  const max = Math.max(...rows.map(row => Math.abs(row.value))) || 1

  return <section className="panel plan-parameters metric-chart" aria-label={title}>
    <h3>{title}</h3>
    <ul>
      {rows.map(row => <li key={row.label}>
        {row.to ? <Link to={row.to} className="text-button">{row.label}</Link> : <span>{row.label}</span>}
        <span className="metric-track" aria-hidden="true">
          <span className={row.value < 0 ? 'metric-negative' : ''} style={{ width: Math.abs(row.value) / max * 100 + '%' }}/>
        </span>
        <strong>{row.display}</strong>
      </li>)}
    </ul>
  </section>
}
