import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'

export interface ProductImportResult { total: number; created: number; updated: number; unchanged: number }

const importOptions = {
  catalog: {
    endpoint: '/products/import',
    title: 'Импорт товаров из Excel',
    submit: 'Загрузить товары',
    failure: 'Не удалось импортировать товары.',
    hint: 'Загрузите книгу номенклатуры с листом «Товары». Новые артикулы будут добавлены, существующие обновятся.',
  },
  prices: {
    endpoint: '/products/price-history/import',
    title: 'Импорт прайса из Excel',
    submit: 'Загрузить прайс',
    failure: 'Не удалось импортировать прайс.',
    hint: 'Загрузите квартальный прайс с листом «Прайс». Товары связываются по артикулу. Записи с теми же датами действия обновятся.',
  },
}

export function ProductImportDialog({ kind = 'catalog', onClose, onImported }: {
  kind?: keyof typeof importOptions
  onClose: () => void
  onImported: (result: ProductImportResult) => void
}) {
  const options = importOptions[kind]
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [issues, setIssues] = useState<Record<string, string>>({})

  function clearErrors() {
    setError('')
    setIssues({})
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearErrors()
    const form = new FormData(event.currentTarget)
    const file = form.get('file')
    if (!(file instanceof File) || !file.name.toLowerCase().endsWith('.xlsx')) {
      setError('Выберите файл в формате XLSX.')
      return
    }
    if (file.size > 1024 * 1024) {
      setError('Размер файла должен быть не более 1 МБ.')
      return
    }

    setBusy(true)
    try {
      onImported(await api<ProductImportResult>(options.endpoint, { method: 'POST', body: form }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : options.failure)
      if (cause instanceof ApiError) setIssues(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  const errors = Object.entries(issues).sort(([left], [right]) => left.localeCompare(right, 'ru', { numeric: true }))
  return (
    <AdminDialog title={options.title} busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void upload(event)}>
        {error && (
          <div className="alert alert-error" role="alert">
            {error}
            {errors.length > 0 && (
              <ul className="product-import-errors">
                {errors.map(([label, message]) => <li key={label}><strong>{label}</strong> — {message}</li>)}
              </ul>
            )}
          </div>
        )}
        <label className="admin-field">
          Файл Excel
          <input name="file" type="file" className="product-upload" accept=".xlsx" required disabled={busy}
            onChange={clearErrors}/>
        </label>
        <p className="product-import-note">
          {options.hint} Формат XLSX, до 1 МБ.
        </p>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Проверяем файл…' : options.submit}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
