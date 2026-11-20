import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { api, ApiError } from '../../api/client'
import { formatCents, formatGoalProgress, formatPlanMoney, lineCents, normalizeDecimal } from './planFormat'
import type { PlanDetail, PlanItem, PlanSummary } from './types'

type DraftRow = Pick<PlanItem, 'productId' | 'sku' | 'name' | 'unit' | 'plannedQuantity' | 'plannedUnitPrice'>

export function PlanItemsEditor({ detail, onSaved }: {
  detail: PlanDetail
  onSaved: (plan: PlanSummary) => Promise<void>
}) {
  const [rows, setRows] = useState<DraftRow[]>(() => detail.items.map(item => ({
    ...item, plannedQuantity: item.plannedQuantity.replace('.', ','), plannedUnitPrice: item.plannedUnitPrice.replace('.', ','),
  })))
  const [productId, setProductId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const offerById = useMemo(() => new Map(detail.offers.map(offer => [offer.productId, offer])), [detail.offers])
  const selected = new Set(rows.map(row => row.productId))
  const available = detail.offers.filter(offer => !selected.has(offer.productId))
  const values = rows.map(row => {
    const quantity = normalizeDecimal(row.plannedQuantity, 11, 3)
    const price = normalizeDecimal(row.plannedUnitPrice, 12, 2)
    return {
      productId: row.productId, plannedQuantity: quantity, plannedUnitPrice: price,
      amount: quantity && Number(quantity) > 0 && price !== null ? lineCents(quantity, price) : null,
    }
  })
  const total = values.every(row => row.amount !== null)
    ? values.reduce((sum, row) => sum + (row.amount ?? 0n), 0n)
    : null

  function addProduct() {
    const offer = available.find(item => String(item.productId) === productId)
    if (!offer) return
    setRows(previous => [...previous, { ...offer, plannedQuantity: '', plannedUnitPrice: offer.unitPrice.replace('.', ',') }])
    setProductId('')
    setFields({})
  }

  function editRow(id: number, field: 'plannedQuantity' | 'plannedUnitPrice', value: string) {
    setRows(previous => previous.map(row => row.productId === id ? { ...row, [field]: value } : row))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const submit = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'review'
    const issues: Record<string, string> = {}
    if (submit && !rows.length) issues.items = 'Добавьте хотя бы одну товарную позицию'
    values.forEach((row, index) => {
      if (row.plannedQuantity === null || Number(row.plannedQuantity) <= 0) {
        issues['items[' + index + '].plannedQuantity'] = 'Укажите положительное количество с точностью до трёх знаков'
      }
      if (row.plannedUnitPrice === null) issues['items[' + index + '].plannedUnitPrice'] = 'Укажите цену с точностью до копейки'
    })
    setError('')
    setFields(issues)
    if (Object.keys(issues).length) return

    setBusy(true)
    try {
      const updated = await api<PlanSummary>('/plans/' + detail.plan.id + '/items', {
        method: 'PUT',
        body: JSON.stringify({
          version: detail.plan.version, submit,
          items: values.map(({ productId, plannedQuantity, plannedUnitPrice }) => ({ productId, plannedQuantity, plannedUnitPrice })),
        }),
      })
      await onSaved(updated)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не удалось сохранить позиции.')
      if (cause instanceof ApiError) setFields(cause.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="plan-items-form" onSubmit={event => void save(event)}>
      <div className="plan-section-top">
        <h2>Позиции плана</h2>
        <div className="plan-item-picker">
          <select aria-label="Товар для добавления в план" value={productId}
            onChange={event => setProductId(event.target.value)} disabled={busy || !available.length}>
            <option value="">{available.length ? 'Выберите товар' : 'Нет товаров для добавления'}</option>
            {available.map(offer => <option key={offer.productId} value={offer.productId}>{offer.sku} — {offer.name}</option>)}
          </select>
          <button type="button" className="button button-secondary" onClick={addProduct} disabled={busy || !productId}>
            <Plus size={15} aria-hidden="true"/>Добавить позицию
          </button>
        </div>
      </div>
      {error && <div className="alert alert-error plan-form-alert" role="alert">{error}</div>}
      {fields.items && <p className="field-error plan-form-alert" role="alert">{fields.items}</p>}
      {rows.length > 0 && (
        <div className="table-scroll">
          <table className="product-table plan-items-table">
            <thead><tr>
              <th scope="col">Товар</th>
              <th scope="col">Количество</th>
              <th scope="col">Цена, BYN</th>
              <th scope="col" className="numeric">Сумма, BYN</th>
              <th scope="col">Действия</th>
            </tr></thead>
            <tbody>
              {rows.map((row, index) => {
                const prefix = 'items[' + index + ']'
                const offer = offerById.get(row.productId)
                const amount = values[index].amount
                return (
                  <tr key={row.productId}>
                    <td className="product-name">
                      <strong>{row.name}</strong>
                      <small className="cell-secondary">{row.sku} · {row.unit}</small>
                      {fields[prefix + '.productId'] && <small className="field-error">{fields[prefix + '.productId']}</small>}
                    </td>
                    <td>
                      <input className="plan-value-input" inputMode="decimal" maxLength={15} aria-label={'Количество ' + row.sku}
                        value={row.plannedQuantity} onChange={event => editRow(row.productId, 'plannedQuantity', event.target.value)}
                        disabled={busy} aria-invalid={!!fields[prefix + '.plannedQuantity']}/>
                      {fields[prefix + '.plannedQuantity'] && <small className="field-error">{fields[prefix + '.plannedQuantity']}</small>}
                    </td>
                    <td>
                      <input className="plan-value-input" inputMode="decimal" maxLength={15} aria-label={'Цена ' + row.sku}
                        value={row.plannedUnitPrice} onChange={event => editRow(row.productId, 'plannedUnitPrice', event.target.value)}
                        disabled={busy} aria-invalid={!!fields[prefix + '.plannedUnitPrice']}/>
                      {offer && <small className="cell-secondary">
                        {formatPlanMoney(offer.unitPrice)} · {offer.priceListCode ?? (offer.fromHistory ? 'История цен' : 'Каталог')}
                      </small>}
                      {fields[prefix + '.plannedUnitPrice'] && <small className="field-error">{fields[prefix + '.plannedUnitPrice']}</small>}
                    </td>
                    <td className="numeric">{amount === null ? '—' : formatCents(amount)}</td>
                    <td>
                      <button type="button" className="icon-button danger-text" aria-label={'Убрать ' + row.sku} disabled={busy}
                        onClick={() => {
                          setRows(previous => previous.filter(line => line.productId !== row.productId))
                          setFields({})
                        }}>
                        <Trash2 size={15} aria-hidden="true"/>
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {rows.length === 0 && <p className="plan-empty-inline">Добавьте товары, которые войдут в региональный план.</p>}
      <footer className="plan-items-footer">
        <div aria-live="polite" className="plan-items-total">
          <strong>Итого {total === null ? '—' : formatCents(total) + ' BYN'}</strong>
          <span>От цели {total === null ? '—' : formatGoalProgress(total, detail.plan.targetRevenue)}</span>
        </div>
        <div className="product-page-actions">
          <button type="submit" className="button button-secondary" disabled={busy}>Сохранить позиции</button>
          <button type="submit" value="review" className="button button-primary" disabled={busy}>Отправить на проверку</button>
        </div>
      </footer>
    </form>
  )
}
