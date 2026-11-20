import { ArrowDown, ArrowDownUp, ArrowUp } from 'lucide-react'

export type SortDirection = 'ascending' | 'descending'

export function SortableTableHeader({ label, direction, numeric, onSort }: {
  label: string
  direction?: SortDirection
  numeric?: boolean
  onSort: () => void
}) {
  const Icon = direction === 'ascending' ? ArrowUp : direction === 'descending' ? ArrowDown : ArrowDownUp
  return (
    <th scope="col" className={numeric ? 'numeric' : undefined} aria-sort={direction}>
      <button type="button" className="product-sort" onClick={onSort}>
        {label}<Icon size={13} aria-hidden="true"/>
      </button>
    </th>
  )
}
