import { useEffect, useState } from 'react'
import { ArrowLeft, Check, Pencil, Send, Undo2 } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { Loading } from '../components/Loading'
import { WorkspaceTabs } from '../components/WorkspaceTabs'
import { PlanRecommendation } from '../features/analytics/AnalysisResults'
import { PlanAnalysisForm } from '../features/analytics/PlanAnalysisForm'
import { PlanRegionIndicators } from '../features/analytics/PlanRegionIndicators'
import { rememberPlan } from '../features/analytics/recentPlans'
import { ShipmentAnalysis } from '../features/analytics/ShipmentAnalysis'
import { useAuth } from '../features/auth/AuthContext'
import { PlanEditor } from '../features/plans/PlanEditor'
import { PlanItemsEditor } from '../features/plans/PlanItemsEditor'
import { PlanReviewDialog } from '../features/plans/PlanReviewDialog'
import { formatCents, formatGoalProgress, formatPeriod, formatPlanMoney, formatPlanQuantity, planCents, planStatusLabel } from '../features/plans/planFormat'
import type { PlanDecision, PlanDetail, PlanSummary } from '../features/plans/types'
import { useResource } from '../hooks/useResource'
import { PageHeading } from './WorkspacePages'
import { PublishedAnalysis } from './AnalyticsPage'

const approvalDate = new Intl.DateTimeFormat('ru-BY', { timeZone: 'Europe/Minsk', dateStyle: 'medium' })

export function PlanDetailPage() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { user } = useAuth()
  const { data: detail, error, loading, refresh } = useResource<PlanDetail>('/plans/' + id, true)
  const [dialog, setDialog] = useState<'EDIT' | PlanDecision | null>(null)
  const [notice, setNotice] = useState('')
  const [failedCheck, setFailedCheck] = useState<PlanSummary | null>(null)
  const [pendingAction, setPendingAction] = useState<'sales' | 'withdraw' | null>(null)
  const [actionError, setActionError] = useState('')
  const analyst = user?.role === 'ANALYST'
  const userId = user?.id
  const openedPlan = detail?.plan
  useEffect(() => {
    if (analyst && userId !== undefined && openedPlan?.id === Number(id)) rememberPlan(userId, openedPlan)
  }, [analyst, id, openedPlan, userId])
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
  const requestedTab = params.get('tab')
  const tab = requestedTab === 'analysis' || requestedTab === 'results' || requestedTab === 'task'
    ? requestedTab : detail.canAnalyze || detail.recommendation ? 'analysis' : 'task'
  const showTask = !analyst || tab === 'task'
  const showResults = !analyst || tab === 'results'
  const progress = formatGoalProgress(planCents(plan.plannedRevenue), plan.targetRevenue)
  const waitingForSales = plan.status === 'SALES_REVISION' || plan.status === 'AWAITING_SALES_DATA'
  const managerWaiting = user?.role === 'MANAGER' && (waitingForSales || plan.status === 'AWAITING_ANALYSIS')
  const sales = detail.salesSubmission
  const shipmentFilters = sales && new URLSearchParams({
    regionId: String(plan.regionId), from: sales.period.from, to: sales.period.to,
  })

  async function onSaved(saved: PlanSummary) {
    setDialog(null)
    setActionError('')
    if (saved.status === 'NEEDS_CORRECTION') {
      setFailedCheck(saved)
      setNotice('')
      return
    }
    if (saved.status === 'APPROVED') setNotice('План утверждён и опубликован для менеджера.')
    else if (saved.status === 'REJECTED') setNotice('Отказ зафиксирован.')
    else if (saved.status === 'IN_REVIEW') setNotice('Проект проверен и передан руководителю.')
    else if (saved.status === 'SALES_REVISION') setNotice('')
    else if (dialog === 'RETURN') setNotice(saved.status === 'AWAITING_ANALYSIS'
      ? 'Замечания к рекомендации переданы аналитику.' : 'Замечания к проекту переданы менеджеру.')
    else if (plan.status === 'IN_REVIEW' && saved.status === 'IN_PROGRESS') setNotice('Проект доступен для редактирования.')
    else if (plan.status === 'AWAITING_ANALYSIS' && saved.status === 'IN_PROGRESS') setNotice('Рекомендация передана менеджеру.')
    else if (saved.status === 'AWAITING_ANALYSIS' && plan.status !== 'AWAITING_ANALYSIS') setNotice('Сведения о продажах проверены и переданы аналитику.')
    else setNotice(dialog === 'EDIT' ? 'Параметры плана сохранены.' : 'Позиции плана сохранены.')
    await refresh()
    setFailedCheck(null)
  }

  async function applyAction(action: 'sales' | 'withdraw') {
    if (pendingAction || loading) return
    setPendingAction(action)
    setNotice('')
    setActionError('')
    try {
      const updated = await api<PlanSummary>('/plans/' + plan.id + '/' + action + '?version=' + plan.version, { method: 'POST' })
      await onSaved(updated)
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'PLAN_CHANGED') {
        setActionError('План изменился. Проверьте обновлённую карточку.')
        await refresh()
      } else {
        setActionError(cause instanceof Error ? cause.message : 'Не удалось выполнить действие.')
      }
    } finally {
      setPendingAction(null)
    }
  }

  return <>
    <PageHeading eyebrow="РЕГИОНАЛЬНЫЙ ПЛАН" title={plan.regionName} subtitle={formatPeriod(plan.periodStart, plan.periodEnd)}>
      <div className="product-page-actions">
        {back}
        {detail.canEditItems && <a href="#plan-items" className="button button-primary">
          <Pencil size={16} aria-hidden="true"/>К позициям плана
        </a>}
        {detail.canWithdraw && (
          <button type="button" className="button button-secondary" disabled={pendingAction !== null || loading} onClick={() => void applyAction('withdraw')}>
            <Undo2 size={16} aria-hidden="true"/>{pendingAction === 'withdraw' ? 'Отзываем…' : 'Отозвать на редактирование'}
          </button>
        )}
        {detail.canEditParameters && (
          <button type="button" className="button button-primary" disabled={loading} onClick={() => setDialog('EDIT')}>
            <Pencil size={16} aria-hidden="true"/>Редактировать задание
          </button>
        )}
        {detail.canReview && <>
          <button type="button" className="button button-secondary" disabled={loading || pendingAction !== null} onClick={() => setDialog('RETURN')}>
            Сформировать замечания
          </button>
          <button type="button" className="button button-secondary danger-text" disabled={loading || pendingAction !== null} onClick={() => setDialog('REJECT')}>
            Оформить отказ
          </button>
          <button type="button" className="button button-primary" disabled={loading || pendingAction !== null} onClick={() => setDialog('APPROVE')}>
            <Check size={16} aria-hidden="true"/>Утвердить план
          </button>
        </>}
      </div>
    </PageHeading>
    {analyst && <WorkspaceTabs label="Карточка регионального плана" active={tab} items={[
      { id: 'task', title: 'Задание', to: '?tab=task' },
      { id: 'analysis', title: 'Анализ и рекомендация', to: '?tab=analysis' },
      { id: 'results', title: 'План и факт', to: '?tab=results' },
    ]}/>}
    {notice && <p className="alert admin-success" role="status">{notice}</p>}
    {actionError && <div className="alert alert-error" role="alert">{actionError}</div>}
    {showTask && <section className="panel plan-parameters" aria-labelledby="plan-parameters-heading">
      <div className="plan-parameters-heading">
        <h2 id="plan-parameters-heading">Задание</h2>
        <span className={'mini-status plan-status-' + plan.status.toLowerCase()}>{planStatusLabel(plan)}</span>
      </div>
      <dl>
        <div><dt>Менеджер</dt><dd>{plan.manager?.name ?? 'Ожидает назначения'}</dd></div>
        {analyst && <div><dt>Целевая выручка</dt><dd>{formatPlanMoney(plan.targetRevenue)}{plan.targetRevenue !== null && ' BYN'}</dd></div>}
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
        <strong>{plan.status === 'APPROVED' ? 'Комментарий к утверждению'
          : plan.status === 'REJECTED' ? 'Причина отказа' : 'Замечание руководителя'}</strong>
        <p className="plan-comment">{detail.reviewComment}</p>
      </div>}
    </section>}

    {showTask && sales && <section className="panel plan-parameters analysis-section" aria-label="Сведения о продажах">
      <div className="plan-parameters-heading"><h2>Сведения о продажах</h2></div>
      <dl>
        <div><dt>Отчётный период</dt><dd>{formatPeriod(sales.period.from, sales.period.to)}</dd></div>
        <div><dt>Строк отгрузок</dt><dd>{sales.shipmentCount.toLocaleString('ru-BY')}</dd></div>
        <div><dt>Фактическая выручка</dt><dd>{formatPlanMoney(sales.period.revenue)} BYN</dd></div>
        {sales.submittedAt && <div><dt>Дата проверки</dt>
          <dd>{approvalDate.format(new Date(sales.submittedAt))}</dd></div>}
      </dl>
      {waitingForSales && sales.shipmentCount === 0 && plan.validationNote && (
        <div className="alert alert-error plan-validation-note" role="alert">{plan.validationNote}</div>
      )}
      {waitingForSales && sales.shipmentCount > 0 && <p className="plan-comment">Отгрузки найдены. Повторная проверка передаст задание на анализ.</p>}
      {waitingForSales && shipmentFilters && <div className="product-page-actions plan-sales-actions">
        <Link to={'/shipments?' + shipmentFilters} className="button button-secondary">Отгрузки за отчётный период</Link>
        {detail.canSubmitSales && <button type="button" className="button button-primary" disabled={pendingAction !== null || loading} onClick={() => void applyAction('sales')}>
          <Send size={16} aria-hidden="true"/>{pendingAction === 'sales' ? 'Проверяем отгрузки…' : 'Проверить отгрузки повторно'}
        </button>}
      </div>}
    </section>}

    {showResults && <div className="plan-metrics">
      <article className="panel plan-metric"><span>Целевая выручка</span>
        <strong>{formatPlanMoney(plan.targetRevenue)}{plan.targetRevenue !== null ? ' BYN' : ''}</strong></article>
      <article className="panel plan-metric"><span>{analyst && plan.status !== 'APPROVED' ? 'Выручка по проекту' : 'Плановая выручка'}</span>
        <strong>{formatPlanMoney(plan.plannedRevenue)} BYN</strong><small>{progress} от цели</small></article>
      <article className="panel plan-metric"><span>Факт прошлого периода</span>
        <strong>{formatPlanMoney(detail.previous.revenue)} BYN</strong>
        <small>{formatPeriod(detail.previous.from, detail.previous.to)}</small></article>
      {detail.actual && <article className="panel plan-metric"><span>Факт планового периода</span>
        <strong>{formatPlanMoney(detail.actual.revenue)} BYN</strong>
        <small>{formatPeriod(detail.actual.from, detail.actual.to)}</small></article>}
      {analyst && detail.actual && <article className="panel plan-metric"><span>Отклонение от плана</span>
        <strong>{formatCents(planCents(detail.actual.revenue) - planCents(plan.plannedRevenue))} BYN</strong>
        <small>Выполнение {formatGoalProgress(planCents(detail.actual.revenue), plan.plannedRevenue)}</small></article>}
    </div>}

    {analyst && tab === 'analysis' && <>
      <ShipmentAnalysis key={plan.id + ':' + detail.previous.from + ':' + detail.previous.to}
        region={{ id: plan.regionId, name: plan.regionName, from: detail.previous.from, to: detail.previous.to }}/>
      <PlanRegionIndicators key={plan.id} regionId={plan.regionId} regionName={plan.regionName} through={detail.previous.to}/>
      {detail.recommendation && <PublishedAnalysis key={plan.id + '-' + plan.version} planId={plan.id} inPlan/>}
      {!detail.canAnalyze && !detail.recommendation && <p className="panel plan-parameters">Рекомендация по этому плану пока не подготовлена.</p>}
    </>}

    {!analyst && detail.recommendation && <PlanRecommendation recommendation={detail.recommendation} linkToCalculation={user?.role !== 'MANAGER'}/>}
    {showResults && detail.planningCheck && <section className="panel plan-parameters analysis-section" aria-label="Результат проверки проекта">
      <div className="plan-parameters-heading"><h2>Результат проверки проекта</h2></div>
      <p className="plan-comment">{detail.planningCheck}</p>
    </section>}
    {showResults && plan.validationNote && !waitingForSales && <div className="alert alert-error plan-validation-note" role="alert">
      <strong>Ошибки проверки позиций</strong>
      <p>{plan.validationNote}</p>
    </div>}
    {detail.canAnalyze && <div hidden={analyst && tab !== 'analysis'}>
      <PlanAnalysisForm key={plan.id + '-' + plan.version} plan={plan} onSaved={onSaved} onReload={refresh}/>
    </div>}
    {analyst && showResults && !detail.items.length && <p className="panel plan-parameters">Менеджер ещё не добавил позиции плана.</p>}
    {showResults && (detail.canEditItems || detail.items.length > 0 || managerWaiting) && <section id="plan-items" className="panel catalog-panel" aria-label="Товарные позиции плана" aria-busy={loading}>
      {detail.canEditItems ? (
        <PlanItemsEditor key={plan.id + '-' + detail.plan.version} detail={{ ...detail, plan }} onSaved={onSaved} onReload={refresh}/>
      ) : <>
        <div className="plan-section-top"><h2>Позиции плана</h2></div>
        {managerWaiting && <p className="plan-empty-inline">
          {waitingForSales
            ? 'Позиции можно будет заполнить после проверки отгрузок и передачи рекомендации аналитиком.'
            : 'Позиции можно будет заполнить после передачи рекомендации аналитиком.'}
        </p>}
        {detail.items.length > 0 && <div className="table-scroll">
            <table className="product-table plan-items-table">
              <thead><tr>
                <th scope="col">Товар</th>
                <th scope="col" className="numeric">Факт прошлого периода</th>
                <th scope="col" className="numeric">Плановое количество</th>
                <th scope="col" className="numeric">Плановая цена, BYN</th>
                <th scope="col" className="numeric">Плановая сумма, BYN</th>
                {detail.actual && <th scope="col" className="numeric">Факт планового периода</th>}
                {analyst && detail.actual && <th scope="col" className="numeric">Отклонение, BYN</th>}
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
                  {analyst && detail.actual && <td className="numeric plan-nowrap">
                    {formatCents(planCents(item.actual?.revenue ?? '0') - planCents(item.plannedAmount))}
                  </td>}
                </tr>)}
              </tbody>
            </table>
        </div>}
      </>}
    </section>}
    {dialog === 'EDIT' && detail.canEditParameters && <PlanEditor detail={detail} onClose={() => setDialog(null)} onSaved={onSaved}/>}
    {detail.canReview && dialog && dialog !== 'EDIT' && (
      <PlanReviewDialog plan={plan} decision={dialog} onClose={() => setDialog(null)} onSaved={onSaved}/>
    )}
  </>
}
