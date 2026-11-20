import { useState } from 'react'
import { ClipboardList, Plus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/AuthContext'
import { PlanEditor } from '../features/plans/PlanEditor'
import { PlanRemovalDialog } from '../features/plans/PlanRemovalDialog'
import { formatPeriod, formatPlanMoney, planStatusLabel } from '../features/plans/planFormat'
import type { PlanSummary } from '../features/plans/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

export function PlansPage({ mode = 'plans' }: { mode?: 'plans' | 'review' | 'analysis' }) {
  const { user } = useAuth()
  const plans = useResource<PlanSummary[]>(mode === 'plans' ? '/plans' : '/plans?attention=true', true)
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const [removal, setRemoval] = useState<{ plan: PlanSummary; action: 'cancel' | 'delete' } | null>(null)
  const [notice, setNotice] = useState('')
  const manage = user?.role === 'SALES_HEAD' && mode === 'plans'
  const expectedStatus = mode === 'analysis' ? 'AWAITING_ANALYSIS' : 'IN_REVIEW'
  const visible = plans.data?.filter(plan => mode === 'plans' || plan.status === expectedStatus) ?? []
  const heading = {
    plans: { title: 'Региональные планы', subtitle: user?.role === 'MANAGER'
      ? 'Задания и проекты планов по вашим регионам.' : 'Параметры планирования и проекты региональных планов.', list: 'Планы продаж', empty: 'Планов пока нет' },
    review: { title: 'Согласование планов', subtitle: 'Проекты с результатами проверки плановых показателей.', list: 'На согласовании', empty: 'Планов на согласовании нет' },
    analysis: { title: 'Покупательная способность', subtitle: 'Задания на расчёт индекса и подготовку рекомендации.', list: 'Задания на анализ', empty: 'Заданий на анализ пока нет' },
  }[mode]

  return <>
    <PageHeading eyebrow={mode === 'analysis' ? 'АНАЛИТИКА' : 'ПЛАНИРОВАНИЕ'} title={heading.title} subtitle={heading.subtitle}>
      {manage && (
        <button type="button" className="button button-primary plan-create" onClick={() => setCreating(true)}>
          <Plus size={17} aria-hidden="true"/>Новый план
        </button>
      )}
    </PageHeading>
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {plans.error && <div className="alert alert-error" role="alert">
      {plans.error}<button type="button" className="text-button" onClick={() => void plans.refresh()}>Повторить</button>
    </div>}
    {plans.loading && !plans.data && <Loading label="Загружаем планы…"/>}
    {plans.data && !plans.error && (
      <section className="panel catalog-panel" aria-labelledby="plan-list-heading" aria-busy={plans.loading}>
        <div className="plan-section-top">
          <h2 id="plan-list-heading">{heading.list} <span className="count-pill">{visible.length}</span></h2>
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
                  <td><div className="plan-row-actions">
                    <Link className="text-button" to={'/plans/' + plan.id}>{mode === 'analysis' ? 'К расчёту' : 'Открыть'}</Link>
                    {manage && plan.status !== 'APPROVED' && <>
                      {plan.status !== 'CANCELLED' && plan.status !== 'REJECTED' && <button type="button" className="text-button danger-text" disabled={plans.loading}
                        onClick={() => setRemoval({ plan, action: 'cancel' })}>Отменить</button>}
                      <button type="button" className="text-button danger-text" disabled={plans.loading}
                        onClick={() => setRemoval({ plan, action: 'delete' })}>Удалить</button>
                    </>}
                  </div></td>
                </tr>)}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">
            <ClipboardList size={40} strokeWidth={1.3} aria-hidden="true"/>
            <h2>{heading.empty}</h2>
            {mode === 'analysis' && <p>Задание появится здесь после проверки отгрузок по региональному плану.</p>}
            {mode === 'plans' && <p>{user?.role === 'MANAGER'
              ? 'Здесь появится задание по региональному плану.'
              : 'Задайте параметры планирования и передайте задание на анализ.'}</p>}
          </div>
        )}
      </section>
    )}
    {creating && <PlanEditor onClose={() => setCreating(false)} onSaved={plan => {
      setCreating(false)
      navigate('/plans/' + plan.id)
    }}/>}
    {removal && <PlanRemovalDialog {...removal} onClose={() => setRemoval(null)} onSaved={async () => {
      setNotice(removal.action === 'delete' ? 'План удалён.' : 'План отменён.')
      setRemoval(null)
      await plans.refresh()
    }}/>}
  </>
}
