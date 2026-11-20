const prices = new Intl.NumberFormat('ru-BY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dates = new Intl.DateTimeFormat('ru-BY', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' })

export function formatPrice(value: number) {
  return prices.format(value)
}

export function formatCatalogDate(value: string) {
  return dates.format(new Date(value + 'T00:00:00Z'))
}

export function catalogSearchText(value: string) {
  return value.normalize().toLocaleLowerCase('ru').replaceAll('ё', 'е')
}
