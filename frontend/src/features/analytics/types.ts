export interface Weights {
  salary: number
  retail: number
  population: number
}

export type StatisticsSource = 'TRAINING' | 'BELSTAT'

export interface RegionalIndicators {
  regionId: number
  regionCode: string
  regionName: string
  averageSalary: number
  incomePerCapita: number | null
  retailPerCapita: number
  population: number
}

export interface Statistics {
  source: StatisticsSource
  periodStart: string
  periodEnd: string
  refinancingRate: number | null
  regions: RegionalIndicators[]
}

export interface RegionalScore {
  indicators: RegionalIndicators
  index: number
  sharePercent: number
}

export interface Calculation extends Omit<Statistics, 'regions'> {
  weights: Weights
  averageIndex: number
  ranking: RegionalScore[]
}

export interface Estimate {
  calculation: Calculation
  selected: RegionalScore
  priceAdjustmentPercent: number
}

export interface Publication {
  planId: number
  regionName: string
  periodStart: string
  periodEnd: string
  publishedAt: string
  author: string
  source: StatisticsSource
  index: number
  text: string
}

export interface Recommendation {
  publication: Publication
  sharePercent: number
  priceAdjustmentPercent: number
}

export interface PublishedCalculation {
  recommendation: Recommendation
  calculation: Calculation
  methodVersion: string
}

export interface StatisticsImportResult {
  year: number
  regions: number
}

export interface SalaryMonth {
  period: string
  sourceUrl: string | null
  regions: { regionCode: string; regionName: string; averageSalary: number }[]
}

export interface SalaryDownloadResult {
  year: number
  months: number
  issues: { period: string; message: string }[]
}
