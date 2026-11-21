import type { PlanSummary } from '../plans/types'

type RecentPlan = Pick<PlanSummary, 'id' | 'regionName' | 'periodStart' | 'periodEnd'>

const key = (userId: number) => 'regional-sales:recent-plans:' + userId

export function recentPlans(userId: number): RecentPlan[] {
  try {
    const items = JSON.parse(sessionStorage.getItem(key(userId)) ?? '[]')
    return Array.isArray(items) ? items : []
  } catch {
    return []
  }
}

export function rememberPlan(userId: number, { id, regionName, periodStart, periodEnd }: RecentPlan) {
  const remaining = recentPlans(userId).filter(plan => plan.id !== id)
  try {
    sessionStorage.setItem(key(userId), JSON.stringify([{ id, regionName, periodStart, periodEnd }, ...remaining].slice(0, 5)))
  } catch {
    // The plan stays accessible if browser storage is disabled.
  }
}
