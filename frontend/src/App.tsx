import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './features/auth/AuthContext'
import { Loading } from './components/Loading'
import { Layout } from './components/Layout'
import { pages, planRoles, priceHistoryPage } from './components/navigation'
import { AuthPage } from './pages/AuthPage'
import { Dashboard, ForbiddenPage, NotFoundPage, PlaceholderPage, ProfilePage } from './pages/WorkspacePages'
import { RegionsPage } from './pages/CatalogPages'
import { ProductsPage } from './pages/ProductsPage'
import { PriceHistoryPage } from './pages/PriceHistoryPage'
import { ShipmentsPage } from './pages/ShipmentsPage'
import { PlansPage } from './pages/PlansPage'
import { PlanDetailPage } from './pages/PlanDetailPage'
import { AdministrationPage } from './pages/AdministrationPage'
import type { Role } from './types'
import './features/plans/plans.css'
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
  const {loading}=useAuth()
  if (loading) return <div className="boot-screen"><Loading label="Подключаем рабочее пространство…"/></div>
  const content: Record<string,React.ReactNode> = {
    '/': <Dashboard/>, '/profile': <ProfilePage/>, '/products': <ProductsPage/>, '/regions': <RegionsPage/>,
    '/shipments': <ShipmentsPage/>, '/administration': <AdministrationPage/>,
    '/plans': <PlansPage/>, '/approvals': <PlansPage reviewOnly/>,
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
          <Route path="/plans/:id" element={<RoleGuard roles={planRoles}><PlanDetailPage/></RoleGuard>}/>
          <Route path="*" element={<NotFoundPage/>}/>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/login" replace/>}/>
    </Routes>
  )
}
