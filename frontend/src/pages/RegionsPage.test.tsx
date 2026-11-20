import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../features/auth/AuthContext'
import type { Region, Role } from '../types'

const regions: Region[] = [
  { code: 'BY-BR', name: 'Брестская область', id: 13, active: true },
  { code: 'BY-VI', name: 'Витебская область', id: 14, active: true },
  { code: 'BY-HM', name: 'г. Минск', id: 15, active: true },
  { code: 'BY-HO', name: 'Гомельская область', id: 16, active: true },
  { code: 'BY-HR', name: 'Гродненская область', id: 17, active: true },
  { code: 'BY-MI', name: 'Минская область', id: 18, active: true },
  { code: 'BY-MA', name: 'Могилёвская область', id: 19, active: true },
]

function openRegions(availableRegions: Region[], role: Role) {
  vi.stubGlobal('fetch', async (path: string) => {
    if (path === '/api/auth/me') {
      return Response.json({
        id: 1, login: 'employee', email: null, fullName: 'Сотрудник',
        role, roleName: role === 'MANAGER' ? 'Менеджер по продажам' : 'Региональный аналитик',
      })
    }
    if (path === '/api/regions') return Response.json(availableRegions)
    throw new Error(`Unexpected request ${path}`)
  })
  render(<MemoryRouter initialEntries={['/regions']}><AuthProvider><App/></AuthProvider></MemoryRouter>)
}

afterEach(() => vi.unstubAllGlobals())

it('shows vehicle registration codes in numerical order for all seven regions', async () => {
  openRegions(regions, 'ANALYST')

  const cards = await screen.findAllByRole('article')
  expect(cards.map(card => [
    within(card).getByText(/^Код \d$/).textContent,
    within(card).getByRole('heading').textContent,
  ])).toEqual([
    ['Код 1', 'Брестская область'],
    ['Код 2', 'Витебская область'],
    ['Код 3', 'Гомельская область'],
    ['Код 4', 'Гродненская область'],
    ['Код 5', 'Минская область'],
    ['Код 6', 'Могилёвская область'],
    ['Код 7', 'г. Минск'],
  ])
  expect(screen.queryByText(/^BY-/)).not.toBeInTheDocument()
})

it('keeps the assigned region code when a manager sees a subset', async () => {
  openRegions(regions.filter(region => region.code === 'BY-HM'), 'MANAGER')

  const card = await screen.findByRole('article')
  expect(within(card).getByRole('heading', { name: 'г. Минск' })).toBeInTheDocument()
  expect(within(card).getByText('Код 7')).toBeInTheDocument()
})
