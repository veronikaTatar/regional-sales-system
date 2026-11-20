export interface Weights {
  salary: number
  retail: number
  population: number
}

export interface RegionalIndicators {
  regionId: number
  regionCode: string
  regionName: string
  averageSalary: number
  incomePerCapita: number
  retailPerCapita: number
  population: number
}

export interface Statistics {
  source: 'TRAINING'
  periodStart: string
  periodEnd: string
  refinancingRate: number
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
  source: 'TRAINING'
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
