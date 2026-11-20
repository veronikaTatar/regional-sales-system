import { XlsxImportDialog } from '../../components/XlsxImportDialog'

export interface ProductImportResult { total: number; created: number; updated: number; unchanged: number }

const importOptions = {
  catalog: {
    endpoint: '/products/import',
    title: 'Импорт каталога из Excel',
    submit: 'Загрузить товары',
    failure: 'Не удалось импортировать товары.',
    hint: 'Загрузите 01_nomenklatura.xlsx или другую книгу с листом «Товары». Новые артикулы будут добавлены, существующие обновятся.',
  },
  prices: {
    endpoint: '/products/price-history/import',
    title: 'Импорт прайсов из Excel',
    submit: 'Загрузить прайсы',
    failure: 'Не удалось импортировать прайсы.',
    hint: 'Выберите один или несколько файлов из папки «02_praisy» (до 24). Артикулы прайсов должны быть в каталоге.',
    maxFiles: 24,
    confirmReplacement: true,
  },
}

export function ProductImportDialog({ kind = 'catalog', onClose, onImported }: {
  kind?: keyof typeof importOptions
  onClose: () => void
  onImported: (result: ProductImportResult) => void
}) {
  return <XlsxImportDialog options={importOptions[kind]} onClose={onClose} onImported={onImported}/>
}
