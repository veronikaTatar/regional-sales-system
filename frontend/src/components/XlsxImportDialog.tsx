import { useState } from 'react'
import { api, ApiError } from '../api/client'
import { AdminDialog } from '../features/administration/AdminDialog'

interface ImportOptions {
  endpoint: string
  title: string
  submit: string
  failure: string
  hint: string
  maxFiles?: number
  confirmReplacement?: boolean
}

export function XlsxImportDialog<Result>({ options, onClose, onImported }: {
  options: ImportOptions
  onClose: () => void
  onImported: (result: Result) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [issues, setIssues] = useState<Record<string, string>>({})
  const [files, setFiles] = useState<File[]>([])
  const [replacementToken, setReplacementToken] = useState<string>()
  const multiple = (options.maxFiles ?? 1) > 1
  const uploadField = multiple ? 'files' : 'file'

  function clearErrors() {
    setError('')
    setIssues({})
    setReplacementToken(undefined)
  }

  async function upload(token?: string) {
    clearErrors()
    if (files.length === 0 || files.some(file => !file.name.toLowerCase().endsWith('.xlsx'))) {
      setError(multiple ? 'Выберите файлы в формате XLSX.' : 'Выберите файл в формате XLSX.')
      return
    }
    if (options.maxFiles && files.length > options.maxFiles) {
      setError('Выберите не более ' + options.maxFiles + ' файлов за одну загрузку.')
      return
    }
    if (files.some(file => file.size > 1024 * 1024)) {
      setError('Размер файла должен быть не более 1 МБ.')
      return
    }

    const form = new FormData()
    for (const file of files) form.append(uploadField, file)
    if (token) form.set('replacementToken', token)
    setBusy(true)
    try {
      onImported(await api<Result>(options.endpoint, { method: 'POST', body: form }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : options.failure)
      if (cause instanceof ApiError) {
        setIssues(cause.fieldErrors)
        if (options.confirmReplacement && cause.code === 'PRICE_PERIOD_EXISTS') setReplacementToken(cause.replacementToken)
      }
    } finally {
      setBusy(false)
    }
  }

  const errors = Object.entries(issues).sort(([left], [right]) => left.localeCompare(right, 'ru', { numeric: true }))
  return (
    <AdminDialog title={options.title} busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => {
        event.preventDefault()
        if (!replacementToken && !busy) void upload()
      }}>
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
          {multiple ? 'Файлы Excel' : 'Файл Excel'}
          <input name={uploadField} type="file" className="product-upload" accept=".xlsx" multiple={multiple}
            required disabled={busy} onChange={event => {
              setFiles(Array.from(event.target.files ?? []))
              clearErrors()
            }}/>
        </label>
        {multiple && files.length > 0 && (
          <details className="product-import-files">
            <summary>Выбрано файлов {files.length}</summary>
            <ul className="product-import-errors">
              {files.map((file, index) => <li key={index}>{file.name}</li>)}
            </ul>
          </details>
        )}
        <p className="product-import-note">
          {options.hint} Формат XLSX, до 1 МБ на файл.
        </p>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          {replacementToken ? (
            <button type="button" className="button button-primary" disabled={busy}
              onClick={() => void upload(replacementToken)}>Заменить и загрузить</button>
          ) : (
            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? (multiple ? 'Проверяем файлы…' : 'Проверяем файл…') : options.submit}
            </button>
          )}
        </footer>
      </form>
    </AdminDialog>
  )
}
