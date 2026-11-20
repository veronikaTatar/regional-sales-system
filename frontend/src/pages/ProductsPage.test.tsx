import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../features/auth/AuthContext'
import type { Product } from '../types'

const catalog: Product[] = Array.from({ length: 27 }, (_, index) => {
  const id = index + 1
  return {
    id, sku: `SP-${id}`, name: `Товар ${id}`, unit: 'шт.', priceAsOf: null,
    category: id % 10 === 0 ? null : id % 2 === 0 ? 'Розетки' : 'Удлинители',
    currentPrice: id * 2.5, description: id === 17 ? 'Заземлённая вилка' : null, active: true,
  }
}).reverse()

function openCatalog(initial: Product[]) {
  let products = initial
  vi.stubGlobal('fetch', async (path: string, options?: RequestInit) => {
    if (path === '/api/auth/me') {
      return Response.json({
        id: 1, login: 'manager', fullName: 'Менеджер каталога', role: 'MANAGER', roleName: 'Менеджер по продажам',
      })
    }
    if (path === '/api/products/manage') return Response.json({ canManage: true, products })
    if (path === '/api/auth/csrf') return Response.json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    const statusPath = /^\/api\/products\/(\d+)\/status$/.exec(path)
    if (statusPath && options?.method === 'PATCH') {
      const { active } = JSON.parse(String(options.body)) as { active: boolean }
      const product = products.find(entry => entry.id === Number(statusPath[1]))!
      const updated = { ...product, active }
      products = products.map(entry => entry.id === updated.id ? updated : entry)
      return Response.json(updated)
    }
    throw new Error(`Unexpected request ${path}`)
  })
  render(<MemoryRouter initialEntries={['/products']}><AuthProvider><App/></AuthProvider></MemoryRouter>)
}

function tableSkus() {
  return Array.from(screen.getByRole('table').querySelectorAll('tbody tr > td:nth-child(2)'), cell => cell.textContent)
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('filters and sorts the whole catalog before paginating', async () => {
  const user = userEvent.setup()
  openCatalog(catalog)
  await screen.findByText('Показано 1–10 из 27')
  const next = screen.getByRole('button', { name: 'Следующая страница' })
  const previous = screen.getByRole('button', { name: 'Предыдущая страница' })
  expect(previous).toBeDisabled()
  await user.click(next)
  expect(tableSkus()[0]).toBe('SP-11')

  const sku = screen.getByRole('button', { name: 'Артикул' })
  await user.click(sku)
  expect(screen.getByRole('columnheader', { name: 'Артикул' })).toHaveAttribute('aria-sort', 'ascending')
  expect(tableSkus().at(-1)).toBe('SP-10')
  await user.click(sku)
  expect(tableSkus()[0]).toBe('SP-27')

  await user.click(screen.getByRole('button', { name: 'Цена, BYN' }))
  expect(tableSkus().slice(0, 4)).toEqual(['SP-1', 'SP-2', 'SP-3', 'SP-4'])
  await user.click(next)
  await user.click(screen.getByRole('searchbox', { name: 'Поиск продукции' }))
  await user.paste('  ЗАЗЕМЛЕННАЯ  sp-17  ')
  expect(tableSkus()).toEqual(['SP-17'])
  expect(screen.getByText('Страница 1 из 1')).toBeInTheDocument()

  const groupFilter = screen.getByRole('combobox', { name: 'Фильтр по товарной группе' })
  await user.selectOptions(groupFilter, within(groupFilter).getByRole('option', { name: 'Розетки' }))
  expect(screen.getByText('Товары по заданным фильтрам не найдены.')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Сбросить фильтры' }))
  expect(screen.getByText('Показано 1–10 из 27')).toBeInTheDocument()

  await user.selectOptions(screen.getByRole('combobox', { name: 'Товаров на странице' }), '25')
  expect(tableSkus()).toHaveLength(25)
  await user.click(screen.getByRole('button', { name: 'Следующая страница' }))
  expect(tableSkus()).toEqual(['SP-26', 'SP-27'])
  expect(screen.getByRole('button', { name: 'Следующая страница' })).toBeDisabled()
})

it('returns to a valid page when archiving removes the last matching row', async () => {
  const user = userEvent.setup()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  openCatalog(catalog.filter(product => product.id <= 11))
  await screen.findByText('Показано 1–10 из 11')
  const statusFilter = screen.getByRole('combobox', { name: 'Фильтр по статусу' })
  await user.selectOptions(statusFilter, 'active')
  await user.click(screen.getByRole('button', { name: 'Следующая страница' }))
  expect(tableSkus()).toEqual(['SP-11'])

  await user.click(screen.getByRole('button', { name: 'Снять с продажи' }))
  await screen.findByText('Показано 1–10 из 10')
  expect(statusFilter).toHaveValue('active')
  expect(screen.getByText('Страница 1 из 1')).toBeInTheDocument()

  await user.selectOptions(statusFilter, 'archived')
  expect(tableSkus()).toEqual(['SP-11'])
  expect(screen.getByRole('button', { name: 'Вернуть в каталог' })).toBeInTheDocument()
})
