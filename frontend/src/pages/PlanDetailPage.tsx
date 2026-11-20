import { useState } from 'react'
import { ArrowLeft, Check, Pencil, Undo2 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { PlanEditor } from '../features/plans/PlanEditor'
import { PlanItemsEditor } from '../features/plans/PlanItemsEditor'
import { PlanReviewDialog } from '../features/plans/PlanReviewDialog'
import { formatGoalProgress, formatPeriod, formatPlanMoney, formatPlanQuantity, planCents, planStatusLabel } from '../features/plans/planFormat'
import type { PlanDetail, PlanSummary } from '../features/plans/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'

const approvalDate = new Intl.DateTimeFormat('ru-BY', { timeZone: 'Europe/Minsk', dateStyle: 'medium' })

export function PlanDetailPage() {
  const { id } = useParams()
  const { data: detail, error, loading, refresh } = useResource<PlanDetail>('/plans/' + id)
  const [dialog, setDialog] = useState<'EDIT' | 'APPROVE' | 'RETURN' | null>(null)
  const [notice, setNotice] = useState('')
  const back = <Link to="/plans" className="button button-secondary"><ArrowLeft size={16} aria-hidden="true"/>К планам</Link>

  if (error) return <>
    <PageHeading title="Региональный план" subtitle="Карточка плана продаж">{back}</PageHeading>
    <div className="alert alert-error" role="alert">
      {error}<button type="button" className="text-button" onClick={() => void refresh()}>Повторить</button>
    </div>
  </>
  if (!detail || detail.plan.id !== Number(id)) return <Loading label="Загружаем план…"/>
  const plan = detail.plan
  const progress = formatGoalProgress(planCents(plan.plannedRevenue), plan.targetRevenue)

  async function onSaved(saved: PlanSummary) {
    setDialog(null)
    if (saved.status === 'APPROVED') setNotice('План утверждён.')
    else if (saved.status === 'IN_REVIEW') setNotice('План передан на проверку.')
    else if (dialog === 'RETURN') setNotice('План возвращён менеджеру на доработку.')
    else if (saved.sentToManagerAt && !plan.sentToManagerAt) setNotice('Задание передано менеджеру.')
    else setNotice(dialog === 'EDIT' ? 'Параметры плана сохранены.' : 'Позиции плана сохранены.')
    await refresh()
  }

  return <>
    <PageHeading eyebrow="РЕГИОНАЛЬНЫЙ ПЛАН" title={plan.regionName} subtitle={formatPeriod(plan.periodStart, plan.periodEnd)}>
      <div className="product-page-actions">
        {back}
        {detail.canEditParameters && (
          <button type="button" className="button button-primary" disabled={loading} onClick={() => setDialog('EDIT')}>
            <Pencil size={16} aria-hidden="true"/>Редактировать задание
          </button>
        )}
        {detail.canReview && <>
          <button type="button" className="button button-secondary" disabled={loading} onClick={() => setDialog('RETURN')}>
            <Undo2 size={16} aria-hidden="true"/>Вернуть на доработку
          </button>
          <button type="button" className="button button-primary" disabled={loading} onClick={() => setDialog('APPROVE')}>
            <Check size={16} aria-hidden="true"/>Утвердить план
          </button>
        </>}
      </div>
    </PageHeading>
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    <section className="panel plan-parameters" aria-labelledby="plan-parameters-heading">
      <div className="plan-parameters-heading">
        <h2 id="plan-parameters-heading">Задание</h2>
        <span className={'mini-status plan-status-' + plan.status.toLowerCase()}>{planStatusLabel(plan)}</span>
      </div>
      <dl>
        <div><dt>Менеджер</dt><dd>{plan.manager?.name ?? 'Ожидает назначения'}</dd></div>
        {detail.budgetLimit !== null && <div><dt>Лимит затрат</dt><dd>{formatPlanMoney(detail.budgetLimit)} BYN</dd></div>}
        {detail.maxDiscountPercent !== null && (
          <div><dt>Максимальная скидка</dt><dd>{formatPlanQuantity(detail.maxDiscountPercent)} %</dd></div>
        )}
        {detail.approvedBy && detail.approvedAt && (
          <div><dt>Утвердил</dt><dd>{detail.approvedBy} · {approvalDate.format(new Date(detail.approvedAt))}</dd></div>
        )}
      </dl>
      {detail.planningComment && <p className="plan-comment">{detail.planningComment}</p>}
      {detail.reviewComment && <div className={'plan-review-note ' + (plan.status === 'APPROVED' ? 'plan-reviewed' : '')}>
        <strong>{plan.status === 'APPROVED' ? 'Комментарий к утверждению' : 'Замечание руководителя'}</strong>
        <p className="plan-comment">{detail.reviewComment}</p>
      </div>}
    </section>

    <div className="plan-metrics">
      <article className="panel plan-metric"><span>Целевая выручка</span>
        <strong>{formatPlanMoney(plan.targetRevenue)}{plan.targetRevenue !== null ? ' BYN' : ''}</strong></article>
      <article className="panel plan-metric"><span>Плановая выручка</span>
        <strong>{formatPlanMoney(plan.plannedRevenue)} BYN</strong><small>{progress} от цели</small></article>
      <article className="panel plan-metric"><span>Факт прошлого периода</span>
        <strong>{formatPlanMoney(detail.previous.revenue)} BYN</strong>
        <small>{formatPeriod(detail.previous.from, detail.previous.to)}</small></article>
      {detail.actual && <article className="panel plan-metric"><span>Факт планового периода</span>
        <strong>{formatPlanMoney(detail.actual.revenue)} BYN</strong>
        <small>{formatPeriod(detail.actual.from, detail.actual.to)}</small></article>}
    </div>

    <section className="panel catalog-panel" aria-label="Товарные позиции плана" aria-busy={loading}>
      {detail.canEditItems ? (
        <PlanItemsEditor key={plan.id + '-' + plan.version} detail={detail} onSaved={onSaved}/>
      ) : <>
        <div className="plan-section-top"><h2>Позиции плана</h2></div>
        {detail.items.length ? (
          <div className="table-scroll">
            <table className="product-table plan-items-table">
              <thead><tr>
                <th scope="col">Товар</th>
                <th scope="col" className="numeric">Факт прошлого периода</th>
                <th scope="col" className="numeric">Плановое количество</th>
                <th scope="col" className="numeric">Плановая цена, BYN</th>
                <th scope="col" className="numeric">Плановая сумма, BYN</th>
                {detail.actual && <th scope="col" className="numeric">Факт планового периода</th>}
              </tr></thead>
              <tbody>
                {detail.items.map(item => <tr key={item.productId}>
                  <td className="product-name"><strong>{item.name}</strong><small className="cell-secondary">{item.sku}</small></td>
                  <td className="numeric plan-nowrap">{formatPlanQuantity(item.previous.quantity)} {item.unit}
                    <small className="cell-secondary">{formatPlanMoney(item.previous.revenue)} BYN</small></td>
                  <td className="numeric plan-nowrap">{formatPlanQuantity(item.plannedQuantity)} {item.unit}</td>
                  <td className="numeric plan-nowrap">{formatPlanMoney(item.plannedUnitPrice)}</td>
                  <td className="numeric plan-nowrap">{formatPlanMoney(item.plannedAmount)}</td>
                  {item.actual && <td className="numeric plan-nowrap">{formatPlanQuantity(item.actual.quantity)} {item.unit}
                    <small className="cell-secondary">{formatPlanMoney(item.actual.revenue)} BYN</small></td>}
                </tr>)}
              </tbody>
            </table>
          </div>
        ) : <p className="plan-empty-inline">Менеджер ещё не добавил позиции в этот план.</p>}
      </>}
    </section>
    {dialog === 'EDIT' && detail.canEditParameters && <PlanEditor detail={detail} onClose={() => setDialog(null)} onSaved={onSaved}/>}
    {detail.canReview && (dialog === 'APPROVE' || dialog === 'RETURN') && (
      <PlanReviewDialog plan={plan} decision={dialog} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
  </>
}
