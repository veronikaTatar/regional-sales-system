import { formatCatalogDate } from '../products/catalogFormat'
import type { PlanStatus, PlanSummary } from './types'

export const planStatusLabels: Record<PlanStatus, string> = {
  DRAFT: 'Черновик',
  AWAITING_ANALYSIS: 'На анализе',
  IN_REVIEW: 'На проверке',
  REVISION: 'На доработке',
  APPROVED: 'Утверждён',
}

const integer = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 0 })

export function formatPeriod(from: string, to: string) {
  return formatCatalogDate(from) + ' — ' + formatCatalogDate(to)
}

export function planStatusLabel(plan: PlanSummary) {
  return plan.status === 'DRAFT' && plan.sentToManagerAt ? 'В работе' : planStatusLabels[plan.status]
}

export function normalizeDecimal(value: string, wholeDigits: number, fractionDigits: number) {
  const normalized = value.trim().replace(',', '.')
  const match = /^(\d+)(?:\.(\d+))?$/.exec(normalized)
  if (!match || match[1].length > wholeDigits || (match[2]?.length ?? 0) > fractionDigits) return null
  return normalized
}

function scaled(value: string, scale: number) {
  const [whole, fractional = ''] = value.split('.')
  return BigInt(whole + fractional.padEnd(scale, '0'))
}

export function planCents(value: string) {
  return scaled(value, 2)
}

export function lineCents(quantity: string, price: string) {
  return (scaled(quantity, 3) * planCents(price) + 500n) / 1000n
}

export function formatCents(value: bigint) {
  const positive = value < 0n ? -value : value
  return (value < 0n ? '−' : '') + integer.format(positive / 100n) + ',' + String(positive % 100n).padStart(2, '0')
}

export function formatPlanMoney(value: string | null) {
  return value === null ? '—' : formatCents(planCents(value))
}

export function formatPlanQuantity(value: string) {
  const [whole, fractional = ''] = value.split('.')
  const decimals = fractional.replace(/0+$/, '')
  return integer.format(BigInt(whole)) + (decimals ? ',' + decimals : '')
}

export function formatGoalProgress(revenue: bigint, goal: string | null) {
  const target = goal === null ? 0n : planCents(goal)
  if (target <= 0n) return '—'
  const tenths = (revenue * 1000n + target / 2n) / target
  return integer.format(tenths / 10n) + ',' + String(tenths % 10n) + ' %'
}
