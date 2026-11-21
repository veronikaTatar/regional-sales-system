import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { StatisticsImportResult } from './types'

const workbooks = [
  { name: 'salary', label: 'Средняя зарплата, BYN в месяц' },
  { name: 'turnover', label: 'Розничный товарооборот за год' },
  { name: 'population', label: 'Численность населения' },
]

export function BelstatImportDialog({ onClose, onImported }: {
  onClose: () => void
  onImported: (result: StatisticsImportResult) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function importFiles(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const files = new FormData(event.currentTarget)
    setError('')
    setBusy(true)
    try {
      const result = await api<StatisticsImportResult>('/analytics/statistics/import', { method: 'POST', body: files })
      onImported(result)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось загрузить статистику.')
    } finally {
      setBusy(false)
    }
  }

  return <AdminDialog title="Импорт данных Белстата" onClose={onClose} busy={busy}>
    <form className="admin-form" onSubmit={event => void importFiles(event)}>
      <p className="form-note">Выбери три файла XLSX с годами и семью регионами. Система возьмёт самый свежий полный год из всех трёх таблиц.</p>
      {error && <div className="alert alert-error" role="alert">{error}</div>}
      <fieldset className="admin-form-grid" disabled={busy}>
        {workbooks.map(item => <label key={item.name} className="admin-field admin-field-wide">
          {item.label}
          <input type="file" name={item.name} accept=".xlsx" required/>
        </label>)}
        <label className="admin-field">
          Единица оборота в файле
          <select name="turnoverUnit" defaultValue="MILLION_BYN">
            <option value="MILLION_BYN">Млн BYN</option>
            <option value="BYN">BYN</option>
          </select>
        </label>
        <label className="admin-field">
          Единица населения в файле
          <select name="populationUnit" defaultValue="PEOPLE">
            <option value="PEOPLE">Человек</option>
            <option value="THOUSAND_PEOPLE">Тыс. человек</option>
          </select>
        </label>
      </fieldset>
      <footer className="admin-dialog-actions">
        <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
        <button type="submit" className="button button-primary" disabled={busy}>{busy ? 'Загружаем…' : 'Загрузить данные'}</button>
      </footer>
    </form>
  </AdminDialog>
}
