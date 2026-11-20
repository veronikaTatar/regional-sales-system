import { useState } from 'react'
import { ClipboardList, Plus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/AuthContext'
import { PlanEditor } from '../features/plans/PlanEditor'
import { formatPeriod, formatPlanMoney, planStatusLabel } from '../features/plans/planFormat'
import type { PlanSummary } from '../features/plans/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function PlansPage({ reviewOnly = false }: { reviewOnly?: boolean }) {
  const { user } = useAuth()
  const plans = useResource<PlanSummary[]>(reviewOnly ? '/plans?attention=true' : '/plans')
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const visible = plans.data?.filter(plan => !reviewOnly || plan.status === 'IN_REVIEW') ?? []

  return <>
    <PageHeading eyebrow="ПЛАНИРОВАНИЕ" title={reviewOnly ? 'Согласование планов' : 'Региональные планы'}
      subtitle={reviewOnly ? 'Планы, которые менеджеры передали на проверку.' : user?.role === 'MANAGER'
        ? 'Ваши задания и планы продаж по закреплённым регионам.' : 'Задания менеджерам и планы продаж по регионам.'}>
      {user?.role === 'SALES_HEAD' && !reviewOnly && (
        <button type="button" className="button button-primary plan-create" onClick={() => setCreating(true)}>
          <Plus size={17} aria-hidden="true"/>Новый план
        </button>
      )}
    </PageHeading>
    {plans.error && <div className="alert alert-error" role="alert">
      {plans.error}<button type="button" className="text-button" onClick={() => void plans.refresh()}>Повторить</button>
    </div>}
    {plans.loading && !plans.data && <Loading label="Загружаем планы…"/>}
    {plans.data && !plans.error && (
      <section className="panel catalog-panel" aria-labelledby="plan-list-heading" aria-busy={plans.loading}>
        <div className="plan-section-top">
          <h2 id="plan-list-heading">{reviewOnly ? 'На проверке' : 'Планы продаж'} <span className="count-pill">{visible.length}</span></h2>
        </div>
        {visible.length ? (
          <div className="table-scroll">
            <table className="product-table plan-list-table">
              <thead><tr>
                <th scope="col">Регион</th>
                <th scope="col">Период</th>
                {user?.role === 'SALES_HEAD' && <th scope="col">Менеджер</th>}
                <th scope="col" className="numeric">Цель, BYN</th>
                <th scope="col" className="numeric">План, BYN</th>
                <th scope="col">Статус</th>
                <th scope="col">Действия</th>
              </tr></thead>
              <tbody>
                {visible.map(plan => <tr key={plan.id}>
                  <td><strong>{plan.regionName}</strong></td>
                  <td className="plan-nowrap">{formatPeriod(plan.periodStart, plan.periodEnd)}</td>
                  {user?.role === 'SALES_HEAD' && <td>{plan.manager?.name ?? '—'}</td>}
                  <td className="numeric plan-nowrap">{formatPlanMoney(plan.targetRevenue)}</td>
                  <td className="numeric plan-nowrap">{formatPlanMoney(plan.plannedRevenue)}</td>
                  <td><span className={'mini-status plan-status-' + plan.status.toLowerCase()}>{planStatusLabel(plan)}</span></td>
                  <td><Link className="text-button" to={'/plans/' + plan.id}>Открыть</Link></td>
                </tr>)}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">
            <ClipboardList size={40} strokeWidth={1.3} aria-hidden="true"/>
            <h2>{reviewOnly ? 'Планов на проверке нет' : 'Планов пока нет'}</h2>
            {!reviewOnly && <p>{user?.role === 'MANAGER'
              ? 'Здесь появятся задания, которые передаст руководитель.'
              : 'Создайте план для региона и передайте его менеджеру.'}</p>}
          </div>
        )}
      </section>
    )}
    {creating && <PlanEditor onClose={() => setCreating(false)} onSaved={plan => {
      setCreating(false)
      navigate('/plans/' + plan.id)
    }}/>}
  </>
}
