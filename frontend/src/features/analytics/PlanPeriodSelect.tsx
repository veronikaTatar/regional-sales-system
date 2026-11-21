import { formatPeriod } from '../plans/planFormat'

type Period = { periodStart: string; periodEnd: string }

export function periodKey(row: Period) {
  return row.periodStart + '/' + row.periodEnd
}

export function PlanPeriodSelect({ rows, value, onChange }: {
  rows: Period[]; value: string; onChange: (value: string) => void
}) {
  const periods = new Map(rows.map(row => [periodKey(row), formatPeriod(row.periodStart, row.periodEnd)]))
  return <label className="admin-field">Период плана
    <select value={value} onChange={event => onChange(event.target.value)}>
      <option value="">Все периоды</option>
      {[...periods.keys()].sort().reverse().map(period => <option key={period} value={period}>{periods.get(period)}</option>)}
    </select>
  </label>
}
