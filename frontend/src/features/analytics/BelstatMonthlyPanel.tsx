import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { api } from '../../api/client'
import { useResource } from '../../hooks/useResource'
import type { MonthlyDownloadResult } from './types'

const monthFormat = new Intl.DateTimeFormat('ru-BY', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const formatMonth = (period: string) => monthFormat.format(new Date(period + '-01T00:00:00Z'))

interface PublishedMonth {
  period: string
  sourceUrl: string | null
}

interface MonthlyPanelProps<Month> {
  id: string
  title: string
  endpoint: string
  emptyMessage: string
  children: (month: Month) => ReactNode
}

export function BelstatMonthlyPanel<Month extends PublishedMonth>({ id, title, endpoint, emptyMessage, children }: MonthlyPanelProps<Month>) {
  const releases = useResource<Month[]>(endpoint)
  const [period, setPeriod] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState('')
  const [download, setDownload] = useState<MonthlyDownloadResult | null>(null)
  const months = releases.data ?? []
  const selected = months.find(month => month.period === period)
    ?? months.find(month => month.period.startsWith(`${download?.year}-`)) ?? months[0]

  async function sync(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const year = String(new FormData(event.currentTarget).get('year')).trim()
    setFailure('')
    setDownload(null)
    setBusy(true)
    try {
      const query = year ? '?year=' + encodeURIComponent(year) : ''
      const result = await api<MonthlyDownloadResult>(endpoint + '/sync' + query, { method: 'POST' })
      setDownload(result)
      setPeriod('')
      await releases.refresh()
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : 'Не удалось скачать выпуски с сайта Белстата.')
    } finally {
      setBusy(false)
    }
  }

  return <section id={id} tabIndex={-1} className="panel plan-parameters analysis-section statistics-section" aria-labelledby={`${id}-heading`}>
    <div className="plan-parameters-heading"><h2 id={`${id}-heading`}>{title}</h2></div>
    <form onSubmit={event => void sync(event)} className="analysis-form">
      <div className="analysis-actions">
        <label className="admin-field statistics-year">Год публикаций
          <input name="year" type="number" min="2000" max="2099" placeholder="Последний доступный" disabled={busy}/>
        </label>
        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? 'Скачиваем выпуски…' : 'Скачать с Белстата'}
        </button>
      </div>
    </form>
    {failure && <p className="alert alert-error" role="alert">{failure}</p>}
    {download && <div className="alert admin-success" role="status">
      Сохранено выпусков за {download.year} год — {download.months}
      {download.issues.length > 0 && <details>
        <summary>Месяцев с ошибкой загрузки — {download.issues.length}</summary>
        <ul>{download.issues.map(issue => <li key={issue.period}>{formatMonth(issue.period)} — {issue.message}</li>)}</ul>
      </details>}
    </div>}
    {releases.error && <div className="alert alert-error" role="alert">{releases.error}
      <button type="button" className="text-button" onClick={() => void releases.refresh()}>Повторить</button>
    </div>}
    {!selected && !releases.loading && !releases.error && (
      <p className="analysis-method-note analysis-inline-note">{emptyMessage}</p>
    )}
    {selected && <>
      <div className="analysis-actions analysis-section">
        <label className="admin-field">Загруженный месяц
          <select value={selected.period} onChange={event => setPeriod(event.target.value)}>
            {months.map(month => <option key={month.period} value={month.period}>{formatMonth(month.period)}</option>)}
          </select>
        </label>
        {selected.sourceUrl && <a className="text-button" href={selected.sourceUrl} target="_blank" rel="noreferrer">
          Скачать исходный {/\.xls(?:[?#]|$)/i.test(selected.sourceUrl) ? 'XLS' : 'XLSX'}
        </a>}
      </div>
      {children(selected)}
    </>}
  </section>
}
