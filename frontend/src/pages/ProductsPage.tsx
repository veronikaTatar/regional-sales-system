import { useState } from 'react'
import { Download, FileUp, Package, Pencil, Plus, Search } from 'lucide-react'
import { api, apiBlob } from '../api/client'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/AuthContext'
import { ProductEditor } from '../features/products/ProductEditor'
import { ProductImportDialog } from '../features/products/ProductImportDialog'
import type { ProductImportResult } from '../features/products/ProductImportDialog'
import { useResource } from '../hooks/useResource'
import type { Product } from '../types'
import { PageHeading } from './WorkspacePages'
import '../features/products/products.css'

interface ManagedCatalog { canManage: boolean; products: Product[] }
type ProductDialog = { kind: 'edit'; product: Product | null } | { kind: 'import' }
type StatusFilter = 'all' | 'active' | 'archived'

const priceFormat = new Intl.NumberFormat('ru-BY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateFormat = new Intl.DateTimeFormat('ru-BY', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' })

export function ProductsPage() {
  const { user } = useAuth()
  const manager = user?.role === 'MANAGER'
  const { data, loading, error, refresh } = useResource<Product[] | ManagedCatalog>(
    manager ? '/products/manage' : '/products',
  )
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState<StatusFilter>('all')
  const [dialog, setDialog] = useState<ProductDialog | null>(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')

  const managed = manager && data && !Array.isArray(data) ? data : null
  const canManage = managed?.canManage === true
  const products = Array.isArray(data) ? data : managed?.products ?? []
  const categories = [...new Set(products.flatMap(product => product.category ? [product.category] : []))]
    .sort((left, right) => left.localeCompare(right, 'ru'))
  const search = query.trim().toLocaleLowerCase('ru')
  const visible = products.filter(product =>
    (!category || product.category === category) &&
    (!canManage || status === 'all' || product.active === (status === 'active')) &&
    [product.name, product.sku, product.category].some(value => value?.toLocaleLowerCase('ru').includes(search)),
  )

  function openDialog(value: ProductDialog) {
    setNotice('')
    setActionError('')
    setDialog(value)
  }

  function onSaved(product: Product) {
    setNotice(dialog?.kind === 'edit' && dialog.product
      ? `Изменения товара «${product.name}» сохранены.`
      : `Товар «${product.name}» добавлен.`)
    setDialog(null)
    void refresh()
  }

  function onImported(result: ProductImportResult) {
    setDialog(null)
    setNotice(`Импорт завершён. Строк ${result.total} · добавлено ${result.created} · обновлено ${result.updated} · без изменений ${result.unchanged}.`)
    void refresh()
  }

  async function exportCatalog() {
    setBusy(true)
    setActionError('')
    try {
      const blob = await apiBlob('/products/export')
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'products.xlsx'
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : 'Не удалось скачать каталог.')
    } finally {
      setBusy(false)
    }
  }

  async function changeStatus(product: Product) {
    if (product.active && !window.confirm(`Снять с продажи товар «${product.name}»?`)) return
    setBusy(true)
    setNotice('')
    setActionError('')
    try {
      const saved = await api<Product>(`/products/${product.id}/status`, {
        method: 'PATCH', body: JSON.stringify({ active: !product.active }),
      })
      setNotice(saved.active ? `Товар «${saved.name}» возвращён в каталог.` : `Товар «${saved.name}» снят с продажи.`)
      await refresh()
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : 'Не удалось изменить статус товара.')
    } finally {
      setBusy(false)
    }
  }

  return <>
    <PageHeading eyebrow="ПРОДАЖИ" title="Продукция" subtitle="Справочник товаров предприятия. Отпускные цены указаны в BYN без НДС.">
      {canManage && (
        <div className="product-page-actions">
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => void exportCatalog()}>
            <Download size={16}/>Скачать Excel
          </button>
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => openDialog({ kind: 'import' })}>
            <FileUp size={16}/>Импорт из Excel
          </button>
          <button className="button button-primary" type="button" disabled={busy} onClick={() => openDialog({ kind: 'edit', product: null })}>
            <Plus size={17}/>Добавить товар
          </button>
        </div>
      )}
    </PageHeading>
    {managed && !managed.canManage && (
      <p className="alert product-access-note">Для ведения каталога администратор должен закрепить за вами регион на текущую дату.</p>
    )}
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {actionError && <p className="alert alert-error" role="alert">{actionError}</p>}
    {error && (
      <div className="alert alert-error" role="alert">
        {error}
        <button className="text-button" type="button" onClick={() => void refresh()}>Повторить</button>
      </div>
    )}

    <section className="panel catalog-panel" aria-labelledby="products-heading" aria-busy={loading}>
      <div className="table-toolbar product-toolbar">
        <h2 id="products-heading">Каталог продукции
          {data && <span className="count-pill">{visible.length === products.length ? products.length : `${visible.length} из ${products.length}`}</span>}
        </h2>
        <div className="product-filters">
          <label className="search-input">
            <Search size={17}/>
            <input type="search" aria-label="Поиск продукции" placeholder="Название, артикул, группа"
              value={query} onChange={event => setQuery(event.target.value)}/>
          </label>
          {categories.length > 0 && (
            <select aria-label="Фильтр по товарной группе" value={category} onChange={event => setCategory(event.target.value)}>
              <option value="">Все группы</option>
              {categories.map(group => <option key={group} value={group}>{group}</option>)}
            </select>
          )}
          {canManage && (
            <select aria-label="Фильтр по статусу" value={status} onChange={event => setStatus(event.target.value as StatusFilter)}>
              <option value="all">Все статусы</option>
              <option value="active">В продаже</option>
              <option value="archived">Снятые с продажи</option>
            </select>
          )}
        </div>
      </div>

      {loading && !data && <Loading label="Загружаем каталог…"/>}
      {data && products.length === 0 && (
        <div className="empty-state">
          <Package size={42} strokeWidth={1.3}/>
          <h2>Каталог пока пуст</h2>
          {canManage && <p>Добавьте товар вручную или загрузите книгу номенклатуры.</p>}
        </div>
      )}
      {products.length > 0 && visible.length === 0 && (
        <div className="table-no-results">
          Товары по заданным фильтрам не найдены.
          <button className="text-button" type="button" onClick={() => { setQuery(''); setCategory(''); setStatus('all') }}>
            Сбросить фильтры
          </button>
        </div>
      )}
      {visible.length > 0 && (
        <div className="table-scroll">
          <table className="product-table">
            <thead>
              <tr>
                <th scope="col">Продукция</th>
                <th scope="col">Артикул</th>
                <th scope="col">Группа</th>
                <th scope="col">Ед. изм.</th>
                <th scope="col" className="numeric">Цена, BYN</th>
                <th scope="col">Статус</th>
                {canManage && <th scope="col" className="numeric">Действия</th>}
              </tr>
            </thead>
            <tbody>
              {visible.map(product => (
                <tr key={product.id}>
                  <td className="product-name">
                    <strong>{product.name}</strong>
                    {product.description && <small className="cell-secondary">{product.description}</small>}
                  </td>
                  <td className="monospace">{product.sku}</td>
                  <td>{product.category ?? '—'}</td>
                  <td>{product.unit}</td>
                  <td className="numeric product-price">
                    {priceFormat.format(product.currentPrice)}
                    {product.priceAsOf && (
                      <small className="cell-secondary">на {dateFormat.format(new Date(product.priceAsOf + 'T00:00:00Z'))}</small>
                    )}
                  </td>
                  <td><span className={`mini-status ${product.active ? '' : 'inactive'}`}>
                    {product.active ? 'В продаже' : 'Снят с продажи'}
                  </span></td>
                  {canManage && (
                    <td>
                      <div className="admin-row-actions product-row-actions">
                        <button type="button" className="text-button" disabled={busy}
                          aria-label={`Изменить ${product.sku}`} onClick={() => openDialog({ kind: 'edit', product })}>
                          <Pencil size={13}/>Изменить
                        </button>
                        <button type="button" className={`text-button ${product.active ? 'danger-text' : ''}`} disabled={busy}
                          onClick={() => void changeStatus(product)}>
                          {product.active ? 'Снять с продажи' : 'Вернуть в каталог'}
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>

    {canManage && dialog?.kind === 'import' && (
      <ProductImportDialog onClose={() => setDialog(null)} onImported={onImported}/>
    )}
    {canManage && dialog?.kind === 'edit' && (
      <ProductEditor product={dialog.product} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
  </>
}
