import { useMemo, useState } from 'react'
import { Download, FileUp, History, Package, Pencil, Plus, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, apiBlob } from '../api/client'
import { Loading } from '../components/Loading'
import { SortableTableHeader } from '../components/SortableTableHeader'
import type { SortDirection } from '../components/SortableTableHeader'
import { priceHistoryPage } from '../components/navigation'
import { useAuth } from '../features/auth/AuthContext'
import { catalogSearchText, formatCatalogDate, formatPrice } from '../features/products/catalogFormat'
import { CatalogPagination, defaultCatalogPageSize } from '../features/products/CatalogPagination'
import { ProductEditor } from '../features/products/ProductEditor'
import { ProductImportDialog } from '../features/products/ProductImportDialog'
import type { ProductImportResult } from '../features/products/ProductImportDialog'
import { useResource } from '../hooks/useResource'
import type { Product } from '../types'
import { PageHeading } from './WorkspacePages'
import '../features/products/products.css'

interface ManagedCatalog { canManage: boolean; products: Product[] }
type ProductDialog = { kind: 'edit'; product: Product | null; categories: string[] } | { kind: 'import' }
type StatusFilter = 'all' | 'active' | 'archived'

const sortColumns = [
  { field: 'name', label: 'Продукция' },
  { field: 'sku', label: 'Артикул' },
  { field: 'category', label: 'Группа' },
  { field: 'unit', label: 'Ед. изм.' },
  { field: 'currentPrice', label: 'Цена, BYN' },
  { field: 'active', label: 'Статус' },
] as const
type SortField = typeof sortColumns[number]['field']
type SortOrder = { field: SortField; direction: SortDirection }

const emptyProducts: Product[] = []
const groupPrefix = 'group:'
const collator = new Intl.Collator('ru', { numeric: true, sensitivity: 'base' })

function compareProducts(left: Product, right: Product, field: SortField) {
  if (field === 'currentPrice') return left.currentPrice - right.currentPrice
  if (field === 'active') return Number(right.active) - Number(left.active)
  return collator.compare(left[field] ?? '', right[field] ?? '')
}

export function ProductsPage({ embedded = false }: { embedded?: boolean }) {
  const { user } = useAuth()
  const manager = user?.role === 'MANAGER'
  const { data, loading, error, refresh } = useResource<Product[] | ManagedCatalog>(
    manager ? '/products/manage' : '/products',
  )
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | null>('')
  const [status, setStatus] = useState<StatusFilter>('all')
  const [sort, setSort] = useState<SortOrder>({ field: 'name', direction: 'ascending' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(defaultCatalogPageSize)
  const [dialog, setDialog] = useState<ProductDialog | null>(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')

  const managed = manager && data && !Array.isArray(data) ? data : null
  const canManage = managed?.canManage === true
  const products = Array.isArray(data) ? data : managed?.products ?? emptyProducts
  const categories = useMemo(() => {
    const names = products.flatMap(product => product.category ? [product.category] : [])
    return [...new Set(names)].sort(collator.compare)
  }, [products])
  const matching = useMemo(() => {
    const terms = catalogSearchText(query).trim().split(/\s+/).filter(Boolean)
    const direction = sort.direction === 'ascending' ? 1 : -1
    return products.filter(product => {
      if (category !== '' && (product.category || null) !== category) return false
      if (canManage && status !== 'all' && product.active !== (status === 'active')) return false
      if (terms.length === 0) return true
      const text = catalogSearchText([product.name, product.sku, product.category, product.description].join(' '))
      return terms.every(term => text.includes(term))
    }).sort((left, right) => direction * compareProducts(left, right, sort.field) || left.id - right.id)
  }, [products, query, category, canManage, status, sort])

  const pageCount = Math.max(1, Math.ceil(matching.length / pageSize))
  const currentPage = Math.min(page, pageCount)
  // A catalog update can remove the last page for the current filters.
  if (page !== currentPage) setPage(currentPage)
  const start = (currentPage - 1) * pageSize
  const visible = matching.slice(start, start + pageSize)
  const hasFilters = query !== '' || category !== '' || (canManage && status !== 'all')

  function changeSort(field: SortField) {
    setSort(previous => ({
      field,
      direction: previous.field === field && previous.direction === 'ascending' ? 'descending' : 'ascending',
    }))
    setPage(1)
  }

  function clearFilters() {
    setQuery('')
    setCategory('')
    setStatus('all')
    setPage(1)
  }

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
    {!embedded && <PageHeading eyebrow="ПРОДАЖИ" title="Продукция" subtitle="Справочник товаров предприятия. Отпускные цены указаны в BYN без НДС.">
      {canManage && (
        <div className="product-page-actions">
          <Link className="button button-secondary" to={priceHistoryPage.path}>
            <History size={16} aria-hidden="true"/>История цен
          </Link>
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => void exportCatalog()}>
            <Download size={16}/>Скачать каталог
          </button>
          <button className="button button-secondary" type="button" disabled={busy} onClick={() => openDialog({ kind: 'import' })}>
            <FileUp size={16}/>Импорт каталога
          </button>
          <button className="button button-primary" type="button" disabled={busy}
            onClick={() => openDialog({ kind: 'edit', product: null, categories })}>
            <Plus size={17}/>Добавить товар
          </button>
        </div>
      )}
    </PageHeading>}
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
          {data && <span className="count-pill">
            {matching.length === products.length ? products.length : `${matching.length} из ${products.length}`}
          </span>}
        </h2>
        <div className="product-filters">
          <label className="search-input">
            <Search size={17} aria-hidden="true"/>
            <input type="search" aria-label="Поиск продукции" placeholder="Название, артикул, группа, характеристики"
              value={query} onChange={event => { setQuery(event.target.value); setPage(1) }}/>
          </label>
          {(categories.length > 0 || category !== '') && (
            <select aria-label="Фильтр по товарной группе" value={category === null ? 'ungrouped' : groupPrefix + category}
              onChange={event => {
                const value = event.target.value
                setCategory(value === 'ungrouped' ? null : value.slice(groupPrefix.length))
                setPage(1)
              }}>
              <option value={groupPrefix}>Все группы</option>
              {categories.map(group => <option key={group} value={groupPrefix + group}>{group}</option>)}
              {category && !categories.includes(category) && <option value={groupPrefix + category}>{category}</option>}
              {(category === null || products.some(product => !product.category)) && <option value="ungrouped">Без группы</option>}
            </select>
          )}
          {canManage && (
            <select aria-label="Фильтр по статусу" value={status}
              onChange={event => { setStatus(event.target.value as StatusFilter); setPage(1) }}>
              <option value="all">Все статусы</option>
              <option value="active">В продаже</option>
              <option value="archived">Снятые с продажи</option>
            </select>
          )}
          {hasFilters && <button className="text-button product-clear-filters" type="button" onClick={clearFilters}>
            Сбросить фильтры
          </button>}
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
      {products.length > 0 && matching.length === 0 && (
        <div className="table-no-results" role="status">Товары по заданным фильтрам не найдены.</div>
      )}
      {visible.length > 0 && (
        <div className="table-scroll">
          <table className="product-table">
            <thead>
              <tr>
                {sortColumns.map(({ field, label }) => {
                  if (field === 'active' && !canManage) return <th key={field} scope="col">{label}</th>
                  const direction = sort.field === field ? sort.direction : undefined
                  return <SortableTableHeader key={field} label={label} direction={direction}
                    numeric={field === 'currentPrice'} onSort={() => changeSort(field)}/>
                })}
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
                    {formatPrice(product.currentPrice)}
                    {product.priceAsOf && (
                      <small className="cell-secondary">на {formatCatalogDate(product.priceAsOf)}</small>
                    )}
                  </td>
                  <td><span className={`mini-status ${product.active ? '' : 'inactive'}`}>
                    {product.active ? 'В продаже' : 'Снят с продажи'}
                  </span></td>
                  {canManage && (
                    <td>
                      <div className="admin-row-actions product-row-actions">
                        <button type="button" className="text-button" disabled={busy}
                          aria-label={`Изменить ${product.sku}`} onClick={() => openDialog({ kind: 'edit', product, categories })}>
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
      {visible.length > 0 && (
        <CatalogPagination page={currentPage} pageSize={pageSize} total={matching.length} onPageChange={setPage}
          onPageSizeChange={size => { setPageSize(size); setPage(1) }}/>
      )}
    </section>

    {canManage && dialog?.kind === 'import' && (
      <ProductImportDialog onClose={() => setDialog(null)} onImported={onImported}/>
    )}
    {canManage && dialog?.kind === 'edit' && (
      <ProductEditor product={dialog.product} categories={dialog.categories} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
  </>
}
