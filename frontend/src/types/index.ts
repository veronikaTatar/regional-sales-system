export type Role = 'ADMIN' | 'ANALYST' | 'MANAGER' | 'SALES_HEAD'
export interface User {
  id: number
  login: string
  email: string | null
  fullName: string
  role: Role
  roleName: string
}
export interface RegisterRequest { login: string; email: string; fullName: string; password: string }
export interface Region { id: number; code: string; name: string; active: boolean }
export interface Product {
  id: number
  sku: string
  name: string
  category: string | null
  unit: string
  description: string | null
  currentPrice: number
  priceAsOf: string | null
  active: boolean
}
