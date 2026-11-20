import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { Shipment, ShipmentOptions } from './types'

const belarusDate = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Minsk', dateStyle: 'short' })

export function ShipmentEditor({ options, onClose, onSaved }: {
  options: ShipmentOptions
  onClose: () => void
  onSaved: (shipment: Shipment) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const activeProducts = options.products.filter(product => product.active)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const quantity = String(form.get('quantity')).trim().replace(',', '.')
    const unitPrice = String(form.get('unitPrice')).trim().replace(',', '.')
    const issues: Record<string, string> = {}
    if (!/^\d{1,11}(\.\d{1,3})?$/.test(quantity) || Number(quantity) <= 0) {
      issues.quantity = 'Введите количество больше нуля с точностью до трёх знаков'
    }
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(unitPrice)) issues.unitPrice = 'Введите цену с точностью до копейки'
    setError('')
    setFields(issues)
    if (Object.keys(issues).length > 0) return

    setBusy(true)
    try {
      onSaved(await api<Shipment>('/shipments', {
        method: 'POST',
        body: JSON.stringify({
          shipmentDate: form.get('shipmentDate'),
          regionId: Number(form.get('regionId')),
          productId: Number(form.get('productId')),
          quantity,
          unitPrice,
          referenceNumber: form.get('referenceNumber'),
        }),
      }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось зарегистрировать отгрузку.')
      if (cause instanceof ApiError) setFields(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminDialog title="Новая отгрузка" busy={busy} onClose={onClose}>
      <form className="admin-form" onSubmit={event => void save(event)}>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <fieldset className="admin-form-grid" disabled={busy}>
          <label className="admin-field">
            Дата отгрузки
            <input name="shipmentDate" type="date" defaultValue={belarusDate.format(new Date())}
              min="1000-01-01" max="9999-12-31" required aria-invalid={Boolean(fields.shipmentDate)}/>
            {fields.shipmentDate && <small className="field-error">{fields.shipmentDate}</small>}
          </label>
          <label className="admin-field">
            Регион
            <select name="regionId" defaultValue={options.regions.length === 1 ? options.regions[0].id : ''}
              required aria-invalid={Boolean(fields.regionId)}>
              <option value="" disabled>Выберите регион</option>
              {options.regions.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
            </select>
            {fields.regionId && <small className="field-error">{fields.regionId}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Товар
            <select name="productId" defaultValue="" required aria-invalid={Boolean(fields.productId)}>
              <option value="" disabled>Выберите товар</option>
              {activeProducts.map(product => <option key={product.id} value={product.id}>
                {product.sku} — {product.name}
              </option>)}
            </select>
            {fields.productId && <small className="field-error">{fields.productId}</small>}
          </label>
          <label className="admin-field">
            Количество
            <input name="quantity" inputMode="decimal" maxLength={15} required aria-invalid={Boolean(fields.quantity)}/>
            {fields.quantity && <small className="field-error" role="alert">{fields.quantity}</small>}
          </label>
          <label className="admin-field">
            Цена единицы без НДС, BYN
            <input name="unitPrice" inputMode="decimal" maxLength={15} required aria-invalid={Boolean(fields.unitPrice)}/>
            {fields.unitPrice && <small className="field-error" role="alert">{fields.unitPrice}</small>}
          </label>
          <label className="admin-field admin-field-wide">
            Ключ строки документа
            <input name="referenceNumber" maxLength={80} placeholder="OTG-2026-0001-01"
              aria-invalid={Boolean(fields.referenceNumber)}/>
            {fields.referenceNumber && <small className="field-error">{fields.referenceNumber}</small>}
          </label>
        </fieldset>
        <p className="product-import-note">Введите цену единицы из документа с учётом скидки.</p>
        <footer className="admin-dialog-actions">
          <button type="button" className="button button-secondary" disabled={busy} onClick={onClose}>Отмена</button>
          <button className="button button-primary" type="submit" disabled={busy}>
            {busy ? 'Сохраняем…' : 'Зарегистрировать'}
          </button>
        </footer>
      </form>
    </AdminDialog>
  )
}
