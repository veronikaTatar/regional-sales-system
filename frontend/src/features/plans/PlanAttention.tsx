import { ArrowRight, ClipboardList } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useResource } from '../../hooks/useResource'
import { formatPeriod } from './planFormat'
import type { PlanSummary } from './types'

const tasks = {
  DRAFT: 'Заполнить план',
  AWAITING_ANALYSIS: 'Подготовить анализ',
  IN_PROGRESS: 'Заполнить план',
  NEEDS_CORRECTION: 'Исправить данные плана',
  IN_REVIEW: 'Проверить план',
  REVISION: 'Доработать план',
  APPROVED: 'Открыть план',
  CANCELLED: 'Открыть план',
}

export function PlanAttention() {
  const { data, error, refresh } = useResource<PlanSummary[]>('/plans?attention=true')
  if (error) return <div className="alert alert-error" role="alert">
    Не удалось загрузить задания по планам.
    <button type="button" className="text-button" onClick={() => void refresh()}>Повторить</button>
  </div>
  if (!data?.length) return null
  return (
    <section className="panel plan-attention" aria-labelledby="plan-attention-heading">
      <div className="plan-section-top">
        <h2 id="plan-attention-heading">Требует внимания <span className="count-pill">{data.length}</span></h2>
      </div>
      <ul>
        {data.slice(0, 5).map(plan => (
          <li key={plan.id}>
            <Link to={'/plans/' + plan.id}>
              <span className="card-icon"><ClipboardList size={17} aria-hidden="true"/></span>
              <span>
                <strong>{tasks[plan.status]}</strong>
                <small>{plan.regionName} · {formatPeriod(plan.periodStart, plan.periodEnd)}</small>
              </span>
              <ArrowRight size={16} aria-hidden="true"/>
            </Link>
          </li>
        ))}
      </ul>
      {data.length > 5 && <Link className="text-button plan-all-tasks" to={data[0].status === 'IN_REVIEW' ? '/approvals'
        : data[0].status === 'AWAITING_ANALYSIS' ? '/purchasing-power' : '/plans'}>
        Открыть список заданий
      </Link>}
    </section>
  )
}
