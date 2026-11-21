import { useEffect, useState } from 'react'
import { FileUp, Plus, Search, Truck } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { SortableTableHeader } from '../components/SortableTableHeader'
import type { SortDirection } from '../components/SortableTableHeader'
import { XlsxImportDialog } from '../components/XlsxImportDialog'
import { useAuth } from '../features/auth/AuthContext'
import { CatalogPagination, defaultCatalogPageSize } from '../features/products/CatalogPagination'
import { formatCatalogDate, formatPrice } from '../features/products/catalogFormat'
import { ShipmentEditor } from '../features/shipments/ShipmentEditor'
import type { Shipment, ShipmentImportResult, ShipmentOptions, ShipmentPage } from '../features/shipments/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'
import '../features/products/products.css'
import '../features/shipments/shipments.css'

const columns = [
  { field: 'DATE', label: 'Дата отгрузки', numeric: false },
  { field: 'REFERENCE', label: 'Ключ строки', numeric: false },
  { field: 'REGION', label: 'Регион', numeric: false },
  { field: 'PRODUCT', label: 'Продукция', numeric: false },
  { field: 'QUANTITY', label: 'Количество', numeric: true },
  { field: 'PRICE', label: 'Цена единицы', numeric: true },
  { field: 'TOTAL', label: 'Сумма', numeric: true },
] as const
type SortField = typeof columns[number]['field']
const initialFilters = { regionId: '', productId: '', from: '', to: '' }
const units = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 3 })
const wholeCurrency = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 0 })
const importOptions = {
  endpoint: '/shipments/import',
  title: 'Импорт отгрузок из Excel',
  submit: 'Загрузить отгрузки',
  failure: 'Не удалось импортировать отгрузки.',
  hint: 'Загрузите книгу с листом «Отгрузки». Система сохранит строки по вашим действующим назначениям. Артикулы должны быть в каталоге.',
}

function formatTotal(value: string) {
  const [whole, fractional = ''] = value.split('.')
  return wholeCurrency.format(BigInt(whole)) + ',' + fractional.padEnd(2, '0')
}

export function ShipmentsPage() {
  const { user } = useAuth()
  const options = useResource<ShipmentOptions>('/shipments/options')
  const [query] = useSearchParams()
  const [filters, setFilters] = useState(() => ({
    ...initialFilters,
    regionId: query.get('regionId') ?? '',
    productId: query.get('productId') ?? '',
    from: query.get('from') ?? '',
    to: query.get('to') ?? '',
  }))
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [sort, setSort] = useState<{ field: SortField; direction: SortDirection }>({ field: 'DATE', direction: 'descending' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(defaultCatalogPageSize)
  const [dialog, setDialog] = useState<'create' | 'import' | null>(null)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const query = search.trim()
    if (query === appliedSearch) return
    const timeout = window.setTimeout(() => {
      setAppliedSearch(query)
      setPage(1)
    }, 250)
    return () => window.clearTimeout(timeout)
  }, [search, appliedSearch])

  const params = new URLSearchParams({
    page: String(page - 1), size: String(pageSize), sort: sort.field,
    direction: sort.direction === 'ascending' ? 'ASC' : 'DESC',
  })
  if (appliedSearch) params.set('search', appliedSearch)
  for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value)
  const journal = useResource<ShipmentPage>('/shipments?' + params)
  const invalidPeriod = filters.from !== '' && filters.to !== '' && filters.to < filters.from
  const error = invalidPeriod ? 'Дата окончания должна быть не раньше даты начала.' : journal.error
  const anyFilters = search !== '' || Object.values(filters).some(Boolean)
  const writable = options.data?.canRecord && !options.error
  const availableProducts = options.data?.products.some(product => product.active)
  const visible = journal.data?.shipments ?? []

  function changeFilter(field: keyof typeof initialFilters, value: string) {
    setFilters(previous => ({ ...previous, [field]: value }))
    setPage(1)
  }

  function clearFilters() {
    setFilters(initialFilters)
    setSearch('')
    setAppliedSearch('')
    setPage(1)
  }

  function changeSort(field: SortField) {
    setSort(previous => ({
      field,
      direction: previous.field === field && previous.direction === 'ascending' ? 'descending' : 'ascending',
    }))
    setPage(1)
  }

  function openDialog(value: 'create' | 'import') {
    setNotice('')
    setDialog(value)
  }

  function onSaved(shipment: Shipment) {
    setNotice(shipment.referenceNumber
      ? 'Отгрузка «' + shipment.referenceNumber + '» зарегистрирована.'
      : 'Отгрузка зарегистрирована.')
    setDialog(null)
    void journal.refresh()
  }

  function onImported(result: ShipmentImportResult) {
    setNotice('Импорт завершён. Добавлено ' + result.created + ' · уже в журнале ' + result.unchanged
      + ' · вне назначений ' + result.skipped + '.')
    setDialog(null)
    void journal.refresh()
  }

  return <>
    <PageHeading eyebrow={user?.role === 'ANALYST' ? undefined : 'ПРОДАЖИ'} title={user?.role === 'ANALYST' ? 'Журнал отгрузок' : 'Отгрузки'} subtitle="Фактические отгрузки по регионам. Цены и суммы указаны в BYN без НДС.">
      {writable && (
        <div className="product-page-actions">
          <button className="button button-secondary" type="button" onClick={() => openDialog('import')}>
            <FileUp size={16} aria-hidden="true"/>Импорт из Excel
          </button>
          <button className="button button-primary" type="button" disabled={!availableProducts} onClick={() => openDialog('create')}>
            <Plus size={17} aria-hidden="true"/>Новая отгрузка
          </button>
        </div>
      )}
    </PageHeading>
    {user?.role === 'MANAGER' && options.data && !options.error && !options.data.canRecord && (
      <p className="alert product-access-note">Для работы с отгрузками администратор должен закрепить за вами регион на текущую дату.</p>
    )}
    {writable && !availableProducts && (
      <p className="alert product-access-note">Для ручного ввода добавьте активный товар в каталог продукции.</p>
    )}
    {options.error && <div className="alert alert-error" role="alert">
      {options.error}<button className="text-button" type="button" onClick={() => void options.refresh()}>Повторить</button>
    </div>}
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {error && <div className="alert alert-error" role="alert">
      {error}{!invalidPeriod && <button className="text-button" type="button" onClick={() => void journal.refresh()}>Повторить</button>}
    </div>}

    <section className="panel catalog-panel" aria-labelledby="shipment-journal-heading" aria-busy={journal.loading}>
      <div className="table-toolbar product-toolbar">
        <h2 id="shipment-journal-heading">Журнал отгрузок
          {journal.data && !error && <span className="count-pill">{journal.data.total}</span>}
        </h2>
        <div className="product-filters shipment-filters">
          <label className="search-input">
            <Search size={17} aria-hidden="true"/>
            <input type="search" aria-label="Поиск отгрузок" placeholder="Документ, товар, артикул" value={search}
              maxLength={120} onChange={event => setSearch(event.target.value)}/>
          </label>
          {options.data && <>
            <select aria-label="Фильтр по региону" value={filters.regionId}
              onChange={event => changeFilter('regionId', event.target.value)}>
              <option value="">Все регионы</option>
              {options.data.regions.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
            </select>
            <select aria-label="Фильтр по товару" value={filters.productId}
              onChange={event => changeFilter('productId', event.target.value)}>
              <option value="">Все товары</option>
              {options.data.products.map(product => <option key={product.id} value={product.id}>
                {product.sku} — {product.name}
              </option>)}
            </select>
          </>}
          <div className="shipment-date-range">
            <label className="shipment-date-filter">
              С <input type="date" aria-label="Дата с" value={filters.from} onChange={event => changeFilter('from', event.target.value)}/>
            </label>
            <label className="shipment-date-filter">
              По <input type="date" aria-label="Дата по" value={filters.to} onChange={event => changeFilter('to', event.target.value)}/>
            </label>
          </div>
          {anyFilters && <button className="text-button product-clear-filters" type="button" onClick={clearFilters}>
            Сбросить фильтры
          </button>}
        </div>
      </div>

      {journal.loading && !journal.data && <Loading label="Загружаем отгрузки…"/>}
      {journal.data && !journal.loading && !error && journal.data.total === 0 && (
        anyFilters ? <div className="table-no-results" role="status">Отгрузки по заданным фильтрам не найдены.</div>
          : <div className="empty-state">
            <Truck size={42} strokeWidth={1.3} aria-hidden="true"/>
            <h2>Журнал отгрузок пока пуст</h2>
            {writable && <p>Зарегистрируйте отгрузку или загрузите квартальный файл.</p>}
          </div>
      )}
      {journal.data && !error && visible.length > 0 && <>
        <div className="table-scroll">
          <table className="product-table shipment-table">
            <thead><tr>
              {columns.map(column => <SortableTableHeader key={column.field} label={column.label} numeric={column.numeric}
                direction={column.field === sort.field ? sort.direction : undefined} onSort={() => changeSort(column.field)}/>)}
            </tr></thead>
            <tbody>
              {visible.map(shipment => <tr key={shipment.id}>
                <td>{formatCatalogDate(shipment.shipmentDate)}</td>
                <td className="monospace shipment-reference">{shipment.referenceNumber ?? '—'}</td>
                <td>{shipment.regionName}</td>
                <td className="product-name">
                  <strong>{shipment.productName}</strong><small className="cell-secondary monospace">{shipment.sku}</small>
                </td>
                <td className="numeric">{units.format(shipment.quantity)} {shipment.unit}</td>
                <td className="numeric">{formatPrice(shipment.unitPrice)}</td>
                <td className="numeric">{formatTotal(shipment.totalAmount)}</td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <CatalogPagination page={journal.data.page + 1} pageSize={journal.data.pageSize} total={journal.data.total}
          pageSizeLabel="Отгрузок на странице" navigationLabel="Страницы журнала отгрузок" onPageChange={setPage}
          onPageSizeChange={size => { setPageSize(size); setPage(1) }}/>
      </>}
    </section>
    {writable && options.data && dialog === 'create' && (
      <ShipmentEditor options={options.data} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
    {writable && dialog === 'import' && (
      <XlsxImportDialog options={importOptions} onClose={() => setDialog(null)} onImported={onImported}/>
    )}
  </>
}
