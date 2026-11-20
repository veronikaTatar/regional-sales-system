import type { Recommendation } from '../analytics/types'

export type PlanStatus = 'DRAFT' | 'AWAITING_SALES_DATA' | 'SALES_REVISION' | 'AWAITING_ANALYSIS' | 'IN_PROGRESS'
  | 'NEEDS_CORRECTION' | 'IN_REVIEW' | 'REVISION' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type PlanDecision = 'APPROVE' | 'RETURN' | 'REJECT'

export interface PlanManager {
  id: number
  name: string
}

export interface PlanRegion {
  id: number
  name: string
  managers: PlanManager[]
}

export interface PlanSummary {
  id: number
  version: number
  regionId: number
  regionName: string
  manager: PlanManager | null
  periodStart: string
  periodEnd: string
  status: PlanStatus
  sentToManagerAt: string | null
  targetRevenue: string | null
  plannedRevenue: string
  validationNote: string | null
}

export interface PlanOffer {
  productId: number
  sku: string
  name: string
  unit: string
  unitPrice: string
  fromHistory: boolean
  priceListCode: string | null
  suggestedUnitPrice: string | null
}

interface Sales {
  quantity: string
  revenue: string
}

interface SalesPeriod {
  from: string
  to: string
  revenue: string
}

export interface PlanItem {
  productId: number
  sku: string
  name: string
  unit: string
  plannedQuantity: string
  plannedUnitPrice: string
  plannedAmount: string
  previous: Sales
  actual: Sales | null
}

export interface PlanDetail {
  plan: PlanSummary
  budgetLimit: string | null
  maxDiscountPercent: string | null
  planningComment: string | null
  reviewComment: string | null
  approvedBy: string | null
  approvedAt: string | null
  canEditParameters: boolean
  canEditItems: boolean
  canReview: boolean
  canAnalyze: boolean
  canWithdraw: boolean
  canSubmitSales: boolean
  recommendation: Recommendation | null
  items: PlanItem[]
  offers: PlanOffer[]
  previous: SalesPeriod
  actual: SalesPeriod | null
  salesSubmission: { period: SalesPeriod; shipmentCount: number; submittedAt: string | null } | null
  planningCheck: string | null
}

export interface PlanResult {
  plan: PlanSummary
  actualRevenue: string
}
