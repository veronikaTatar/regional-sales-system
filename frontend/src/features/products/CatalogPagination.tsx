import { ChevronLeft, ChevronRight } from 'lucide-react'

const pageSizes = [10, 25, 50]
export const defaultCatalogPageSize = pageSizes[0]

interface Props {
  page: number
  pageSize: number
  total: number
  pageSizeLabel?: string
  navigationLabel?: string
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
}

export function CatalogPagination({ page, pageSize, total, onPageChange, onPageSizeChange,
  pageSizeLabel = 'Товаров на странице', navigationLabel = 'Страницы каталога' }: Props) {
  const pageCount = Math.ceil(total / pageSize)
  return (
    <div className="product-pagination">
      <p aria-live="polite">Показано {(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} из {total}</p>
      <div className="product-pagination-controls">
        <label className="product-page-size">
          На странице
          <select aria-label={pageSizeLabel} value={pageSize}
            onChange={event => onPageSizeChange(Number(event.target.value))}>
            {pageSizes.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <nav className="product-page-navigation" aria-label={navigationLabel}>
          <button className="button button-secondary" type="button" aria-label="Предыдущая страница"
            disabled={page === 1} onClick={() => onPageChange(page - 1)}>
            <ChevronLeft size={16} aria-hidden="true"/>
          </button>
          <span>Страница {page} из {pageCount}</span>
          <button className="button button-secondary" type="button" aria-label="Следующая страница"
            disabled={page === pageCount} onClick={() => onPageChange(page + 1)}>
            <ChevronRight size={16} aria-hidden="true"/>
          </button>
        </nav>
      </div>
    </div>
  )
}
