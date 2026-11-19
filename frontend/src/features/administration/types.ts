import type { Role, User } from '../../types'

export interface ManagedUser extends User {
  active: boolean
  hasRegionAssignments?: boolean
}

export interface RoleOption {
  code: Role
  name: string
}

export interface RegionAssignment {
  id: number
  regionId: number
  regionName: string
  regionActive: boolean
  startDate: string
  endDate: string | null
}
