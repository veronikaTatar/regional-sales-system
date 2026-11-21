import { LayoutDashboard, Package, MapPinned, Truck, Database, Calculator, ChartNoAxesCombined, Lightbulb, ClipboardList, BadgeCheck, FileChartColumnIncreasing, UsersRound, UserRound, History } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Role } from '../types'
export interface PageDefinition { path: string; title: string; subtitle: string; icon: LucideIcon; group: string; roles: Role[]; capabilities?: string[] }
const all: Role[] = ['ADMIN', 'ANALYST', 'MANAGER', 'SALES_HEAD']
export const planRoles: Role[] = ['MANAGER', 'SALES_HEAD']
export const planDetailRoles: Role[] = [...planRoles, 'ANALYST']
export const priceHistoryPage: PageDefinition = {
  path: '/products/price-history', title: 'История цен', subtitle: 'Цены товаров по периодам действия',
  icon: History, group: 'ПРОДАЖИ', roles: ['MANAGER'],
}
export const pages: PageDefinition[] = [
  { path: '/', title: 'Главная панель', subtitle: 'Ваше рабочее пространство', icon: LayoutDashboard, group: 'ОБЗОР', roles: all },
  { path: '/products', title: 'Продукция', subtitle: 'Каталог продукции предприятия', icon: Package, group: 'ПРОДАЖИ', roles: ['MANAGER','ANALYST','SALES_HEAD'] },
  { path: '/regions', title: 'Регионы', subtitle: 'География регионального сбыта', icon: MapPinned, group: 'ПРОДАЖИ', roles: all },
  { path: '/shipments', title: 'Отгрузки', subtitle: 'Учёт фактических продаж по регионам', icon: Truck, group: 'ПРОДАЖИ', roles: ['MANAGER','ANALYST','SALES_HEAD'], capabilities: ['Регистрация отгрузок продукции','Фильтры по региону, периоду и товару','Контроль продаж в закреплённых регионах'] },
  { path: '/statistics', title: 'Статистика', subtitle: 'Региональные социально-экономические показатели', icon: Database, group: 'АНАЛИТИКА', roles: ['ANALYST'] },
  { path: '/purchasing-power', title: 'Задания на анализ', subtitle: 'Задания на расчёт индекса и подготовку рекомендаций', icon: Calculator, group: 'ПЛАНИРОВАНИЕ', roles: ['ANALYST'] },
  { path: '/analytics', title: 'Аналитика', subtitle: 'Сравнение показателей региональных рынков', icon: ChartNoAxesCombined, group: 'АНАЛИТИКА', roles: ['ANALYST','SALES_HEAD'] },
  { path: '/recommendations', title: 'Рекомендации', subtitle: 'Аналитическое обоснование сбытовых решений', icon: Lightbulb, group: 'АНАЛИТИКА', roles: ['ANALYST','SALES_HEAD'] },
  { path: '/plans', title: 'Региональные планы', subtitle: 'Планирование региональных продаж', icon: ClipboardList, group: 'ПЛАНИРОВАНИЕ', roles: planRoles },
  { path: '/approvals', title: 'Согласование планов', subtitle: 'Проверка и утверждение планов продаж', icon: BadgeCheck, group: 'ПЛАНИРОВАНИЕ', roles: ['SALES_HEAD'] },
  { path: '/reports', title: 'Отчётность', subtitle: 'Плановые и фактические результаты продаж', icon: FileChartColumnIncreasing, group: 'ПЛАНИРОВАНИЕ', roles: planDetailRoles },
  { path: '/administration', title: 'Пользователи', subtitle: 'Учётные записи и права доступа', icon: UsersRound, group: 'УПРАВЛЕНИЕ', roles: ['ADMIN'] },
  { path: '/profile', title: 'Мой профиль', subtitle: 'Данные вашей учётной записи', icon: UserRound, group: 'УЧЁТНАЯ ЗАПИСЬ', roles: all },
  { path: '/directories', title: 'Справочники', subtitle: 'Продукция и регионы', icon: Package, group: '', roles: ['ANALYST'] },
]

const analystMenu = [
  { path: '/' },
  { path: '/shipments' },
  { path: '/regions' },
  { path: '/analytics', title: 'Аналитика сбыта', group: 'ПЛАНИРОВАНИЕ' },
  { path: '/statistics', title: 'Внешние данные', group: 'ПЛАНИРОВАНИЕ' },
  { path: '/profile' },
]

export function navigationFor(role: Role): PageDefinition[] {
  if (role === 'ANALYST') return analystMenu.flatMap(entry => {
    const page = pages.find(item => item.path === entry.path && item.roles.includes(role))
    return page ? [{ ...page, ...entry }] : []
  })
  return pages.filter(page => page.roles.includes(role))
}
