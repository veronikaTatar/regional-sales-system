import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import type { Product } from '../../types'
import { AdminDialog } from '../administration/AdminDialog'

interface Props {
  product: Product | null
  onClose: () => void
  onSaved: (product: Product) => void
}

export function ProductEditor({ product, onClose, onSaved }: Props) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const currentPrice = String(form.get('currentPrice')).trim().replace(',', '.')
    setError('')
    setFields({})
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(currentPrice)) {
      setFields({ currentPrice: 'Введите цену с точностью до двух знаков после запятой' })
      return
    }

    const input = {
      sku: form.get('sku'),
      name: form.get('name'),
      category: form.get('category'),
      unit: form.get('unit'),
      description: form.get('description'),
      currentPrice,
      priceAsOf: form.get('priceAsOf') || null,
    }
    setBusy(true)
    try {
      const result = await api<Product>(product ? `/products/${product.id}` : '/products', {
        method: product ? 'PUT' : 'POST',
        body: JSON.stringify(input),
      })
      onSaved(result)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить товар.')
      if (cause instanceof ApiError) setFields(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title={product ? 'Редактирование товара' : 'Новый товар'}
      description={product?.sku} busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <fieldset className="admin-form-grid" disabled={busy}>
          <label className="admin-field admin-field-wide">
            Наименование
            <input name="name" defaultValue={product?.name} maxLength={200} required autoFocus
              aria-invalid={Boolean(fields.name)}/>
            {fields.name && <small className="field-error">{fields.name}</small>}
          </label>
          <label className="admin-field">
            Артикул
            <input name="sku" defaultValue={product?.sku} maxLength={50} required aria-invalid={Boolean(fields.sku)}/>
            {fields.sku && <small className="field-error">{fields.sku}</small>}
          </label>
          <label className="admin-field">
            Единица измерения
            <input name="unit" defaultValue={product?.unit ?? 'шт.'} maxLength={20} required
              aria-invalid={Boolean(fields.unit)}/>
            {fields.unit && <small className="field-error">{fields.unit}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Товарная группа
            <input name="category" defaultValue={product?.category ?? ''} maxLength={100}
              aria-invalid={Boolean(fields.category)}/>
            {fields.category && <small className="field-error">{fields.category}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Характеристики
            <textarea name="description" className="product-description" rows={3} maxLength={500}
              defaultValue={product?.description ?? ''} aria-invalid={Boolean(fields.description)}/>
            {fields.description && <small className="field-error">{fields.description}</small>}
          </label>
          <label className="admin-field">
            Цена без НДС, BYN
            <input name="currentPrice" inputMode="decimal" defaultValue={product?.currentPrice ?? ''}
              maxLength={15} required aria-invalid={Boolean(fields.currentPrice)}/>
            {fields.currentPrice && <small className="field-error" role="alert">{fields.currentPrice}</small>}
          </label>
          <label className="admin-field">
            Цена на дату
            <input name="priceAsOf" type="date" defaultValue={product?.priceAsOf ?? ''} min="1000-01-01"
              max="9999-12-31" aria-invalid={Boolean(fields.priceAsOf)}/>
            {fields.priceAsOf && <small className="field-error">{fields.priceAsOf}</small>}
          </label>
        </fieldset>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Отмена</button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Сохраняем…' : 'Сохранить товар'}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
