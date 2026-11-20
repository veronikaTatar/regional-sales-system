import { useState } from 'react'
import { ArrowLeft, Check, Pencil, Undo2 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { Loading } from '../components/Loading'
import { PlanRecommendation } from '../features/analytics/AnalysisResults'
import { PlanAnalysisForm } from '../features/analytics/PlanAnalysisForm'
import { useAuth } from '../features/auth/AuthContext'
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
  const { user } = useAuth()
  const { data: detail, error, loading, refresh } = useResource<PlanDetail>('/plans/' + id)
  const [dialog, setDialog] = useState<'EDIT' | 'APPROVE' | 'RETURN' | null>(null)
  const [notice, setNotice] = useState('')
  const [failedCheck, setFailedCheck] = useState<PlanSummary | null>(null)
  const [withdrawing, setWithdrawing] = useState(false)
  const [withdrawError, setWithdrawError] = useState('')
  const analyst = user?.role === 'ANALYST'
  const back = <Link to={analyst ? '/purchasing-power' : '/plans'} className="button button-secondary">
    <ArrowLeft size={16} aria-hidden="true"/>{analyst ? 'К заданиям' : 'К планам'}</Link>

  if (error) return <>
    <PageHeading title="Региональный план" subtitle="Карточка плана продаж">{back}</PageHeading>
    <div className="alert alert-error" role="alert">
      {error}<button type="button" className="text-button" onClick={() => void refresh()}>Повторить</button>
    </div>
  </>
  if (!detail || detail.plan.id !== Number(id)) return <Loading label="Загружаем план…"/>
  const plan = failedCheck?.id === detail.plan.id && failedCheck.version > detail.plan.version ? failedCheck : detail.plan
  const progress = formatGoalProgress(planCents(plan.plannedRevenue), plan.targetRevenue)

  async function onSaved(saved: PlanSummary) {
    setDialog(null)
    setWithdrawError('')
    if (saved.status === 'NEEDS_CORRECTION') {
      setFailedCheck(saved)
      setNotice('')
      return
    }
    if (saved.status === 'APPROVED') setNotice('План утверждён.')
    else if (saved.status === 'IN_REVIEW') setNotice('План передан на согласование.')
    else if (plan.status === 'IN_REVIEW' && saved.status === 'IN_PROGRESS') setNotice('План отозван. Позиции доступны для редактирования.')
    else if (saved.status === 'AWAITING_ANALYSIS' && plan.status !== 'AWAITING_ANALYSIS') setNotice('Задание передано аналитику.')
    else if (dialog === 'RETURN') setNotice('План возвращён менеджеру на доработку.')
    else if (saved.sentToManagerAt && !plan.sentToManagerAt) setNotice('Рекомендация сохранена. Задание передано менеджеру.')
    else setNotice(dialog === 'EDIT' ? 'Параметры плана сохранены.' : 'Позиции плана сохранены.')
    await refresh()
    setFailedCheck(null)
  }

  async function withdraw() {
    if (withdrawing || loading) return
    setWithdrawing(true)
    setNotice('')
    setWithdrawError('')
    try {
      const updated = await api<PlanSummary>('/plans/' + plan.id + '/withdraw?version=' + plan.version, { method: 'POST' })
      await onSaved(updated)
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setWithdrawError('Статус плана изменился. Проверьте карточку.')
        await refresh()
      } else {
        setWithdrawError(cause instanceof Error ? cause.message : 'Не удалось отозвать план.')
      }
    } finally {
      setWithdrawing(false)
    }
  }

  return <>
    <PageHeading eyebrow="РЕГИОНАЛЬНЫЙ ПЛАН" title={plan.regionName} subtitle={formatPeriod(plan.periodStart, plan.periodEnd)}>
      <div className="product-page-actions">
        {back}
        {detail.canWithdraw && (
          <button type="button" className="button button-primary" disabled={withdrawing || loading} onClick={() => void withdraw()}>
            <Undo2 size={16} aria-hidden="true"/>{withdrawing ? 'Отзываем…' : 'Отозвать на редактирование'}
          </button>
        )}
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
    {withdrawError && <div className="alert alert-error" role="alert">{withdrawError}</div>}
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

    {detail.recommendation && <PlanRecommendation recommendation={detail.recommendation} linkToCalculation={user?.role !== 'MANAGER'}/>}
    {plan.validationNote && <div className="alert alert-error plan-validation-note" role="alert">
      <strong>Ошибки проверки данных</strong>
      <p>{plan.validationNote}</p>
    </div>}
    {detail.canAnalyze ? <PlanAnalysisForm key={plan.id + '-' + plan.version} plan={plan} onSaved={onSaved} onReload={refresh}/>
    : <section className="panel catalog-panel" aria-label="Товарные позиции плана" aria-busy={loading}>
      {detail.canEditItems ? (
        <PlanItemsEditor key={plan.id + '-' + detail.plan.version} detail={{ ...detail, plan }} onSaved={onSaved} onReload={refresh}/>
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
        ) : <p className="plan-empty-inline">{plan.status === 'AWAITING_ANALYSIS'
          ? 'Менеджер получит задание после публикации рекомендации.' : 'Менеджер ещё не добавил позиции в этот план.'}</p>}
      </>}
    </section>}
    {dialog === 'EDIT' && detail.canEditParameters && <PlanEditor detail={detail} onClose={() => setDialog(null)} onSaved={onSaved}/>}
    {detail.canReview && (dialog === 'APPROVE' || dialog === 'RETURN') && (
      <PlanReviewDialog plan={plan} decision={dialog} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
  </>
}
