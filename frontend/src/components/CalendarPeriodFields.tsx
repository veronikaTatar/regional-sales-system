type PeriodKind = 'MONTH' | 'QUARTER'
const minskDate = new Intl.DateTimeFormat('sv-SE', { dateStyle: 'short', timeZone: 'Europe/Minsk' })
const months = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
const quarters = ['I квартал', 'II квартал', 'III квартал', 'IV квартал']

export interface PeriodSelection {
  year: string
  kind: PeriodKind
  period: number
}

export function initialPeriod(periodStart?: string, periodEnd?: string): PeriodSelection {
  const [year, month] = (periodStart ?? minskDate.format(new Date())).split('-')
  const kind = periodEnd && periodStart?.slice(0, 7) === periodEnd.slice(0, 7) ? 'MONTH' : 'QUARTER'
  return { year, kind, period: kind === 'MONTH' ? Number(month) : Math.ceil(Number(month) / 3) }
}

export function periodDates({ year, kind, period }: PeriodSelection) {
  const startMonth = kind === 'MONTH' ? period : (period - 1) * 3 + 1
  const endMonth = kind === 'MONTH' ? startMonth : startMonth + 2
  const lastDay = new Date(Date.UTC(Number(year), endMonth, 0)).getUTCDate()
  const monthText = (month: number) => String(month).padStart(2, '0')
  const calendarYear = String(Number(year))
  return {
    periodStart: calendarYear + '-' + monthText(startMonth) + '-01',
    periodEnd: calendarYear + '-' + monthText(endMonth) + '-' + lastDay,
  }
}

export function CalendarPeriodFields({ value, onChange, error }: {
  value: PeriodSelection
  onChange: (value: PeriodSelection) => void
  error?: string
}) {
  return <>
    <label className="admin-field">
      Год
      <input name="year" type="number" min="1000" max="9999" step="1" value={value.year} required
        onChange={event => onChange({ ...value, year: event.target.value })}/>
    </label>
    <label className="admin-field">
      Вид периода
      <select name="periodKind" value={value.kind} onChange={event => onChange({ ...value, kind: event.target.value as PeriodKind, period: 1 })}>
        <option value="QUARTER">Квартал</option>
        <option value="MONTH">Месяц</option>
      </select>
    </label>
    <label className="admin-field admin-field-wide">
      {value.kind === 'MONTH' ? 'Месяц' : 'Квартал'}
      <select name="period" value={value.period} onChange={event => onChange({ ...value, period: Number(event.target.value) })}>
        {(value.kind === 'MONTH' ? months : quarters).map((label, index) => <option key={label} value={index + 1}>{label}</option>)}
      </select>
      {error && <small className="field-error">{error}</small>}
    </label>
  </>
}
