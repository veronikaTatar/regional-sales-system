import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { ProductImportResult } from './ProductImportDialog'

const calendar = new Intl.DateTimeFormat('ru-BY', { timeZone: 'Europe/Minsk', year: 'numeric', month: 'numeric' })
const quarters = ['I квартал', 'II квартал', 'III квартал', 'IV квартал']

export function QuarterSnapshotDialog({ onClose, onSaved }: {
  onClose: () => void
  onSaved: (result: ProductImportResult) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [issues, setIssues] = useState<Record<string, string>>({})
  const parts = calendar.formatToParts(new Date())
  const year = parts.find(part => part.type === 'year')?.value
  const month = Number(parts.find(part => part.type === 'month')?.value)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    setIssues({})
    try {
      const result = await api<ProductImportResult>('/products/price-history/snapshot', {
        method: 'POST',
        body: JSON.stringify({ year: Number(form.get('year')), quarter: Number(form.get('quarter')) }),
      })
      onSaved(result)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить цены.')
      if (cause instanceof ApiError) setIssues(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title="Сохранить цены за квартал" busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && (
          <div className="alert alert-error" role="alert">
            {error}
            {Object.keys(issues).length > 0 && (
              <ul className="product-import-errors">
                {Object.entries(issues).map(([label, message]) => <li key={label}>{message}</li>)}
              </ul>
            )}
          </div>
        )}
        <fieldset className="admin-form-grid" disabled={busy}>
          <label className="admin-field">
            Год
            <input name="year" type="number" min={1000} max={9999} step={1} defaultValue={year} required/>
          </label>
          <label className="admin-field">
            Квартал
            <select name="quarter" defaultValue={Math.ceil(month / 3)} required>
              {quarters.map((quarter, index) => <option key={quarter} value={index + 1}>{quarter}</option>)}
            </select>
          </label>
        </fieldset>
        <p className="product-import-note">
          Будут добавлены отпускные цены активных товаров, для которых ещё нет записи за выбранный квартал.
        </p>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Сохраняем…' : 'Сохранить цены'}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
