import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { api, ApiError } from '../../api/client'
import { AdminDialog } from '../administration/AdminDialog'
import type { Shipment, ShipmentOptions } from './types'

const belarusDate = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Minsk', dateStyle: 'short' })
const quantityFormat = new Intl.NumberFormat('ru-BY', { useGrouping: false, maximumFractionDigits: 3 })
const quantityPattern = /^\d{1,11}(\.\d{1,3})?$/
const quantityStep = 5
const maxQuantity = 99_999_999_999.999

export function ShipmentEditor({ options, onClose, onSaved }: {
  options: ShipmentOptions
  onClose: () => void
  onSaved: (shipment: Shipment) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const [quantity, setQuantity] = useState('')
  const quantityId = useId()
  const activeProducts = options.products.filter(product => product.active)
  const onlyRegion = options.regions.length === 1 ? options.regions[0] : null
  const normalizedQuantity = quantity.trim().replace(',', '.')
  const amount = quantityPattern.test(normalizedQuantity) ? Number(normalizedQuantity) : null
  const stepBase = normalizedQuantity === '' ? 0 : amount
  const increased = stepBase !== null && stepBase <= maxQuantity - quantityStep ? stepBase + quantityStep : null
  const decreased = amount !== null && amount > quantityStep ? amount - quantityStep : null

  function changeQuantity(value: string) {
    setQuantity(value)
    if (fields.quantity) setFields(previous => ({ ...previous, quantity: '' }))
  }

  function stepQuantity(value: number | null) {
    if (value !== null) changeQuantity(quantityFormat.format(value))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const unitPrice = String(form.get('unitPrice')).trim().replace(',', '.')
    const issues: Record<string, string> = {}
    if (amount === null || amount <= 0) {
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
          quantity: normalizedQuantity,
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
            {onlyRegion ? <>
              <input value={onlyRegion.name} readOnly aria-invalid={Boolean(fields.regionId)}/>
              <input type="hidden" name="regionId" value={onlyRegion.id}/>
            </> : (
              <select name="regionId" defaultValue="" required aria-invalid={Boolean(fields.regionId)}>
                <option value="" disabled>Выберите регион</option>
                {options.regions.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
              </select>
            )}
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
          <div className="admin-field">
            <label htmlFor={quantityId}>Количество</label>
            <div className="shipment-quantity-control">
              <input id={quantityId} name="quantity" inputMode="decimal" maxLength={15} required
                value={quantity} aria-invalid={Boolean(fields.quantity)}
                onChange={event => changeQuantity(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                    event.preventDefault()
                    stepQuantity(event.key === 'ArrowUp' ? increased : decreased)
                  }
                }}/>
              <div className="shipment-quantity-arrows">
                <button type="button" aria-label="Увеличить количество на 5" title="Прибавить 5"
                  disabled={increased === null} onClick={() => stepQuantity(increased)}>
                  <ChevronUp size={14} aria-hidden="true"/>
                </button>
                <button type="button" aria-label="Уменьшить количество на 5" title="Вычесть 5"
                  disabled={decreased === null} onClick={() => stepQuantity(decreased)}>
                  <ChevronDown size={14} aria-hidden="true"/>
                </button>
              </div>
            </div>
            {fields.quantity && <small className="field-error" role="alert">{fields.quantity}</small>}
          </div>
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
