import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { useResource } from '../../hooks/useResource'
import { indicatorFormat } from './AnalysisResults'
import type { SalaryDownloadResult, SalaryMonth } from './types'

const monthFormat = new Intl.DateTimeFormat('ru-BY', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const formatMonth = (period: string) => monthFormat.format(new Date(period + '-01T00:00:00Z'))

export function BelstatSalaries() {
  const salaries = useResource<SalaryMonth[]>('/analytics/statistics/salaries')
  const [period, setPeriod] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState('')
  const [download, setDownload] = useState<SalaryDownloadResult | null>(null)
  const months = salaries.data ?? []
  const selected = months.find(month => month.period === period)
    ?? months.find(month => month.period.startsWith(download?.year + '-')) ?? months[0]

  async function sync(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const year = String(new FormData(event.currentTarget).get('year')).trim()
    setFailure('')
    setDownload(null)
    setBusy(true)
    try {
      const result = await api<SalaryDownloadResult>('/analytics/statistics/salaries/sync' + (year ? '?year=' + year : ''), { method: 'POST' })
      setDownload(result)
      setPeriod('')
      await salaries.refresh()
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : 'Не удалось скачать зарплаты с сайта Белстата.')
    } finally {
      setBusy(false)
    }
  }

  return <section className="panel plan-parameters analysis-section" aria-labelledby="belstat-salaries-heading">
    <div className="plan-parameters-heading"><h2 id="belstat-salaries-heading">Зарплаты Белстата по регионам</h2></div>
    <form onSubmit={event => void sync(event)} className="analysis-form">
      <div className="analysis-actions">
        <label className="admin-field">Год публикаций
          <input name="year" type="number" min="2000" max="2099" placeholder="Последний доступный" disabled={busy}/>
        </label>
        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? 'Скачиваем выпуски…' : 'Скачать с Белстата'}
        </button>
      </div>
    </form>
    {failure && <p className="alert alert-error" role="alert">{failure}</p>}
    {download && <div className="alert admin-success" role="status">
      Сохранено выпусков за {download.year} год: {download.months}
      {download.issues.length > 0 && <details>
        <summary>Не загружено: {download.issues.length}</summary>
        <ul>{download.issues.map(issue => <li key={issue.period}>{formatMonth(issue.period)} — {issue.message}</li>)}</ul>
      </details>}
    </div>}
    {salaries.error && <div className="alert alert-error" role="alert">{salaries.error}
      <button type="button" className="text-button" onClick={() => void salaries.refresh()}>Повторить</button>
    </div>}
    {!selected && !salaries.loading && !salaries.error && (
      <p className="analysis-method-note analysis-section">Месячные зарплаты ещё не загружены.</p>
    )}
    {selected && <>
      <div className="analysis-actions analysis-section">
        <label className="admin-field">Загруженный месяц
          <select value={selected.period} onChange={event => setPeriod(event.target.value)}>
            {months.map(month => <option key={month.period} value={month.period}>{formatMonth(month.period)}</option>)}
          </select>
        </label>
        {selected.sourceUrl && <a className="text-button" href={selected.sourceUrl} target="_blank" rel="noreferrer">
          Скачать исходный XLSX
        </a>}
      </div>
      <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr><th scope="col">Регион</th><th scope="col" className="numeric">Начисленная зарплата, BYN в месяц</th></tr></thead>
          <tbody>{selected.regions.map(region => <tr key={region.regionCode}>
            <th scope="row">{region.regionName}</th>
            <td className="numeric">{indicatorFormat.format(region.averageSalary)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </>}
  </section>
}
