import { useMemo, useState } from 'react'
import { ArrowLeft, CalendarPlus, FileUp, History, Search } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { priceHistoryPage } from '../components/navigation'
import { CatalogPagination, defaultCatalogPageSize } from '../features/products/CatalogPagination'
import { catalogSearchText, formatCatalogDate, formatPrice } from '../features/products/catalogFormat'
import { ProductImportDialog } from '../features/products/ProductImportDialog'
import type { ProductImportResult } from '../features/products/ProductImportDialog'
import { QuarterSnapshotDialog } from '../features/products/QuarterSnapshotDialog'
import { useResource } from '../hooks/useResource'
import type { ProductPrice } from '../types'
import { PageHeading } from './WorkspacePages'
import '../features/products/products.css'

const emptyHistory: ProductPrice[] = []

function periodKey(entry: ProductPrice) {
  return entry.validFrom + '/' + entry.validTo
}

function periodLabel(entry: ProductPrice) {
  return formatCatalogDate(entry.validFrom) + ' — ' + formatCatalogDate(entry.validTo)
}

export function PriceHistoryPage() {
  const { data, loading, error, apiError, refresh } = useResource<ProductPrice[]>('/products/price-history')
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState('')
  const [period, setPeriod] = useState('')
  const [product, setProduct] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(defaultCatalogPageSize)
  const [notice, setNotice] = useState('')
  const history = data ?? emptyHistory
  const dialog = params.get('dialog')
  const errorMessage = apiError?.code === 'INVALID_REQUEST' || apiError?.status === 404
    ? 'Бэкенд не распознал маршрут истории цен. Проверьте версию бэкенда и перезапустите его.'
    : error
  const { periods, products } = useMemo(() => {
    const periods = new Map<string, string>()
    const products = new Map<string, string>()
    for (const entry of history) {
      const key = periodKey(entry)
      if (!periods.has(key)) periods.set(key, periodLabel(entry))
      products.set(entry.sku, entry.name)
    }
    return { periods, products: [...products].sort(([left], [right]) => left.localeCompare(right, 'ru', { numeric: true })) }
  }, [history])
  const matching = useMemo(() => {
    const terms = catalogSearchText(query).trim().split(/\s+/).filter(Boolean)
    return history.filter(entry => {
      if (period && periodKey(entry) !== period) return false
      if (product && entry.sku !== product) return false
      if (terms.length === 0) return true
      const text = catalogSearchText([entry.sku, entry.name, entry.priceListCode].join(' '))
      return terms.every(term => text.includes(term))
    })
  }, [history, query, period, product])
  const currentPage = Math.min(page, Math.max(1, Math.ceil(matching.length / pageSize)))
  if (currentPage !== page) setPage(currentPage)
  const visible = matching.slice((currentPage - 1) * pageSize, currentPage * pageSize)

  function openDialog(value: 'import' | 'snapshot') {
    setNotice('')
    setParams({ dialog: value }, { replace: true })
  }

  function closeDialog() {
    setParams({}, { replace: true })
  }

  function clearFilters() {
    setQuery('')
    setPeriod('')
    setProduct('')
    setPage(1)
  }

  function onSaved(result: ProductImportResult) {
    const prefix = dialog === 'import' ? 'Прайсы загружены.' : 'Цены за квартал сохранены.'
    setNotice(prefix + ' Добавлено ' + result.created + ' · обновлено ' + result.updated
      + ' · без изменений ' + result.unchanged + '.')
    closeDialog()
    clearFilters()
    void refresh()
  }

  return <>
    <PageHeading eyebrow={priceHistoryPage.group} title={priceHistoryPage.title} subtitle={priceHistoryPage.subtitle + '. Цены в BYN.'}>
      <div className="product-page-actions">
        <Link to="/products" className="button button-secondary"><ArrowLeft size={16} aria-hidden="true"/>Продукция</Link>
        {data && !error && <>
          <button className="button button-secondary" type="button" onClick={() => openDialog('snapshot')}>
            <CalendarPlus size={16} aria-hidden="true"/>Сохранить цены из каталога
          </button>
          <button className="button button-primary" type="button" onClick={() => openDialog('import')}>
            <FileUp size={16} aria-hidden="true"/>Импорт прайсов
          </button>
        </>}
      </div>
    </PageHeading>
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {error && (
      <div className="alert alert-error" role="alert">
        {errorMessage}
        <button className="text-button" type="button" onClick={() => void refresh()}>Повторить</button>
      </div>
    )}
    {loading && !data && <Loading label="Загружаем историю цен…"/>}
    {data && !error && (
      <section className="panel catalog-panel" aria-labelledby="price-history-heading" aria-busy={loading}>
        <div className="table-toolbar product-toolbar">
          <h2 id="price-history-heading">Цены по периодам
            <span className="count-pill">
              {matching.length === history.length ? history.length : matching.length + ' из ' + history.length}
            </span>
          </h2>
          {history.length > 0 && (
            <div className="product-filters price-history-filters">
              <label className="search-input">
                <Search size={17} aria-hidden="true"/>
                <input type="search" aria-label="Поиск в истории цен" placeholder="Товар, артикул, код прайса"
                  value={query} onChange={event => { setQuery(event.target.value); setPage(1) }}/>
              </label>
              <select aria-label="Фильтр по периоду" value={period}
                onChange={event => { setPeriod(event.target.value); setPage(1) }}>
                <option value="">Все периоды</option>
                {[...periods].map(([key, label]) => <option value={key} key={key}>{label}</option>)}
              </select>
              <select aria-label="Фильтр по товару" value={product}
                onChange={event => { setProduct(event.target.value); setPage(1) }}>
                <option value="">Все товары</option>
                {products.map(([sku, name]) => <option value={sku} key={sku}>{sku} — {name}</option>)}
              </select>
              {(query || period || product) && <button type="button" className="text-button product-clear-filters" onClick={clearFilters}>
                Сбросить фильтры
              </button>}
            </div>
          )}
        </div>
        {history.length === 0 && (
          <div className="empty-state">
            <History size={42} strokeWidth={1.3} aria-hidden="true"/>
            <h2>История цен пока пуста</h2>
            <p>Загрузите один или несколько прайсов Excel из папки «02_praisy».</p>
          </div>
        )}
        {history.length > 0 && matching.length === 0 && (
          <div className="table-no-results" role="status">Цены по заданным фильтрам не найдены.</div>
        )}
        {visible.length > 0 && <>
          <div className="table-scroll">
            <table className="product-table price-history-table">
              <thead><tr>
                <th scope="col">Продукция</th>
                <th scope="col">Период действия</th>
                <th scope="col" className="numeric">Отпускная без НДС</th>
                <th scope="col" className="numeric">Отпускная с НДС</th>
                <th scope="col" className="numeric">Розничный ориентир</th>
                <th scope="col">Код прайса</th>
              </tr></thead>
              <tbody>
                {visible.map(entry => (
                  <tr key={entry.id}>
                    <td className="product-name">
                      <strong>{entry.name}</strong>
                      <small className="cell-secondary monospace">{entry.sku}</small>
                    </td>
                    <td className="price-history-period">{periodLabel(entry)}</td>
                    <td className="numeric">{formatPrice(entry.unitPrice)}</td>
                    <td className="numeric">
                      {entry.priceWithVat === null ? '—' : formatPrice(entry.priceWithVat)}
                      {entry.vatRate !== null && <small className="cell-secondary">НДС {entry.vatRate} %</small>}
                    </td>
                    <td className="numeric">{entry.retailPrice === null ? '—' : formatPrice(entry.retailPrice)}</td>
                    <td className="monospace">{entry.priceListCode ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <CatalogPagination page={currentPage} pageSize={pageSize} total={matching.length} onPageChange={setPage}
            pageSizeLabel="Записей на странице" navigationLabel="Страницы истории цен"
            onPageSizeChange={size => { setPageSize(size); setPage(1) }}/>
        </>}
      </section>
    )}
    {data && !error && dialog === 'import' && <ProductImportDialog kind="prices" onClose={closeDialog} onImported={onSaved}/>}
    {data && !error && dialog === 'snapshot' && <QuarterSnapshotDialog onClose={closeDialog} onSaved={onSaved}/>}
  </>
}
