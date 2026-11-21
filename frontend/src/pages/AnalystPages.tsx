import { Navigate, useSearchParams } from 'react-router-dom'
import { WorkspaceTabs } from '../components/WorkspaceTabs'
import { ShipmentAnalysis } from '../features/analytics/ShipmentAnalysis'
import { AnalyticsPage } from './AnalyticsPage'
import { RegionsPage } from './CatalogPages'
import { PlansPage } from './PlansPage'
import { ProductsPage } from './ProductsPage'
import { ReportsPage } from './ReportsPage'
import { PageHeading } from './WorkspacePages'

export function AnalystTasksPage() {
  const [params] = useSearchParams()
  const history = params.get('tab') === 'recommendations'
  return <>
    <PageHeading title="Задания на анализ" subtitle="Региональные планы и выводы, переданные менеджерам."/>
    <WorkspaceTabs label="Задания на анализ" active={history ? 'recommendations' : 'pending'} items={[
      { id: 'pending', title: 'К анализу', to: '/purchasing-power' },
      { id: 'recommendations', title: 'Переданные рекомендации', to: '/purchasing-power?tab=recommendations' },
    ]}/>
    {history ? <AnalyticsPage history embedded/> : <PlansPage mode="analysis" embedded/>}
  </>
}

export function SalesAnalysisPage() {
  const [params] = useSearchParams()
  const legacyPlan = params.get('plan')
  if (legacyPlan && /^\d+$/.test(legacyPlan)) return <Navigate to={'/plans/' + legacyPlan + '?tab=analysis'} replace/>
  const report = params.get('tab') === 'plan-fact'
  return <>
    <PageHeading title="Аналитика сбыта" subtitle="Динамика отгрузок предприятия и выполнение утверждённых планов."/>
    <WorkspaceTabs label="Аналитика сбыта" active={report ? 'plan-fact' : 'shipments'} items={[
      { id: 'shipments', title: 'Анализ отгрузок', to: '/analytics' },
      { id: 'plan-fact', title: 'План и факт', to: '/analytics?tab=plan-fact' },
    ]}/>
    {report ? <ReportsPage embedded/> : <ShipmentAnalysis/>}
  </>
}

export function AnalystDirectoriesPage() {
  const [params] = useSearchParams()
  const regions = params.get('tab') === 'regions'
  return <>
    <PageHeading title="Справочники" subtitle="Каталог товаров и география сбыта."/>
    <WorkspaceTabs label="Справочники" active={regions ? 'regions' : 'products'} items={[
      { id: 'products', title: 'Продукция', to: '/directories' },
      { id: 'regions', title: 'Регионы', to: '/directories?tab=regions' },
    ]}/>
    {regions ? <RegionsPage embedded/> : <ProductsPage embedded/>}
  </>
}
