import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './features/auth/AuthContext'
import { Loading } from './components/Loading'
import { Layout } from './components/Layout'
import { pages, planDetailRoles, priceHistoryPage } from './components/navigation'
import { AuthPage } from './pages/AuthPage'
import { Dashboard, ForbiddenPage, NotFoundPage, PlaceholderPage, ProfilePage } from './pages/WorkspacePages'
import { RegionsPage } from './pages/CatalogPages'
import { ProductsPage } from './pages/ProductsPage'
import { PriceHistoryPage } from './pages/PriceHistoryPage'
import { ShipmentsPage } from './pages/ShipmentsPage'
import { PlansPage } from './pages/PlansPage'
import { PlanDetailPage } from './pages/PlanDetailPage'
import { AdministrationPage } from './pages/AdministrationPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AnalystDirectoriesPage, AnalystTasksPage, SalesAnalysisPage } from './pages/AnalystPages'
import { ReportsPage } from './pages/ReportsPage'
import { StatisticsPage } from './pages/StatisticsPage'
import type { Role } from './types'
import './features/plans/plans.css'
import './features/analytics/analytics.css'
function RequireAuth() {
  const { user } = useAuth()
  const location = useLocation()
  return user ? <Outlet/> : <Navigate to="/login" state={{from:location.pathname}} replace/>
}
function RoleGuard({roles,children}: {roles:Role[];children:React.ReactNode}) {
  const {user}=useAuth()
  return user&&roles.includes(user.role) ? children : <ForbiddenPage/>
}
export function App() {
  const {loading,user}=useAuth()
  if (loading) return <div className="boot-screen"><Loading label="Подключаем рабочее пространство…"/></div>
  const analyst = user?.role === 'ANALYST'
  const content: Record<string,React.ReactNode> = {
    '/': <Dashboard/>, '/profile': <ProfilePage/>, '/products': <ProductsPage/>, '/regions': <RegionsPage/>,
    '/shipments': <ShipmentsPage/>, '/administration': <AdministrationPage/>,
    '/plans': <PlansPage/>, '/approvals': <PlansPage mode="review"/>, '/purchasing-power': <AnalystTasksPage/>,
    '/statistics': <StatisticsPage/>, '/analytics': analyst ? <SalesAnalysisPage/> : <AnalyticsPage/>,
    '/recommendations': analyst ? <Navigate to="/purchasing-power?tab=recommendations" replace/> : <AnalyticsPage history/>,
    '/reports': analyst ? <Navigate to="/analytics?tab=plan-fact" replace/> : <ReportsPage/>,
    '/directories': <AnalystDirectoriesPage/>,
  }
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login"/>}/>
      <Route path="/register" element={<AuthPage mode="register"/>}/>
      <Route element={<RequireAuth/>}>
        <Route element={<Layout/>}>
          {pages.map(page => <Route key={page.path} path={page.path}
            element={<RoleGuard roles={page.roles}>{content[page.path] ?? <PlaceholderPage page={page}/>}</RoleGuard>}/>)}
          <Route path={priceHistoryPage.path} element={<RoleGuard roles={priceHistoryPage.roles}><PriceHistoryPage/></RoleGuard>}/>
          <Route path="/plans/:id" element={<RoleGuard roles={planDetailRoles}><PlanDetailPage/></RoleGuard>}/>
          <Route path="*" element={<NotFoundPage/>}/>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/login" replace/>}/>
    </Routes>
  )
}
