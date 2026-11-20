import { LayoutDashboard, Package, MapPinned, Truck, Database, Calculator, ChartNoAxesCombined, Lightbulb, ClipboardList, BadgeCheck, FileChartColumnIncreasing, UsersRound, UserRound, History } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Role } from '../types'
export interface PageDefinition { path: string; title: string; subtitle: string; icon: LucideIcon; group: string; roles: Role[]; capabilities?: string[] }
const all: Role[] = ['ADMIN', 'ANALYST', 'MANAGER', 'SALES_HEAD']
export const planRoles: Role[] = ['MANAGER', 'SALES_HEAD']
export const priceHistoryPage: PageDefinition = {
  path: '/products/price-history', title: 'История цен', subtitle: 'Цены товаров по периодам действия',
  icon: History, group: 'ПРОДАЖИ', roles: ['MANAGER'],
}
export const pages: PageDefinition[] = [
  { path: '/', title: 'Главная панель', subtitle: 'Ваше рабочее пространство', icon: LayoutDashboard, group: 'ОБЗОР', roles: all },
  { path: '/products', title: 'Продукция', subtitle: 'Каталог продукции предприятия', icon: Package, group: 'ПРОДАЖИ', roles: ['MANAGER','ANALYST','SALES_HEAD'] },
  { path: '/regions', title: 'Регионы', subtitle: 'География регионального сбыта', icon: MapPinned, group: 'ПРОДАЖИ', roles: all },
  { path: '/shipments', title: 'Отгрузки', subtitle: 'Учёт фактических продаж по регионам', icon: Truck, group: 'ПРОДАЖИ', roles: ['MANAGER','ANALYST','SALES_HEAD'], capabilities: ['Регистрация отгрузок продукции','Фильтры по региону, периоду и товару','Контроль продаж в закреплённых регионах'] },
  { path: '/statistics', title: 'Статистика', subtitle: 'Региональные социально-экономические показатели', icon: Database, group: 'АНАЛИТИКА', roles: ['ANALYST'], capabilities: ['Импорт статистики из CSV и XLSX','Проверка данных и поиск дубликатов','Журнал загрузок и ошибок обработки'] },
  { path: '/purchasing-power', title: 'Покупательная способность', subtitle: 'Оценка экономического потенциала регионов', icon: Calculator, group: 'АНАЛИТИКА', roles: ['ANALYST'], capabilities: ['Настройка периода и весов показателей','Нормализация исходной статистики','Расчёт и сохранение региональных индексов'] },
  { path: '/analytics', title: 'Аналитика', subtitle: 'Сравнение показателей региональных рынков', icon: ChartNoAxesCombined, group: 'АНАЛИТИКА', roles: ['ANALYST','SALES_HEAD'], capabilities: ['Сравнение регионов на диаграммах','Динамика индекса покупательной способности','Сводные показатели региональных рынков'] },
  { path: '/recommendations', title: 'Рекомендации', subtitle: 'Аналитическое обоснование сбытовых решений', icon: Lightbulb, group: 'АНАЛИТИКА', roles: ['ANALYST','SALES_HEAD'], capabilities: ['Рекомендации на основе расчётов','История аналитических заключений','Связь рекомендаций с региональными планами'] },
  { path: '/plans', title: 'Региональные планы', subtitle: 'Планирование региональных продаж', icon: ClipboardList, group: 'ПЛАНИРОВАНИЕ', roles: planRoles },
  { path: '/approvals', title: 'Согласование планов', subtitle: 'Проверка и утверждение планов продаж', icon: BadgeCheck, group: 'ПЛАНИРОВАНИЕ', roles: ['SALES_HEAD'] },
  { path: '/reports', title: 'Отчётность', subtitle: 'Плановые и фактические результаты продаж', icon: FileChartColumnIncreasing, group: 'ПЛАНИРОВАНИЕ', roles: ['MANAGER','ANALYST','SALES_HEAD'], capabilities: ['Сопоставление плана и факта продаж','Фильтры по периоду и региону','Экспорт отчётных данных в Excel'] },
  { path: '/administration', title: 'Пользователи', subtitle: 'Учётные записи и права доступа', icon: UsersRound, group: 'УПРАВЛЕНИЕ', roles: ['ADMIN'] },
  { path: '/profile', title: 'Мой профиль', subtitle: 'Данные вашей учётной записи', icon: UserRound, group: 'УЧЁТНАЯ ЗАПИСЬ', roles: all },
]
