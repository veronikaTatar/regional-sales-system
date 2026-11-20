import type { Region } from '../../types'

const registrationCodes = new Map([
  ['BY-BR', 1],
  ['BY-VI', 2],
  ['BY-HO', 3],
  ['BY-HR', 4],
  ['BY-MI', 5],
  ['BY-MA', 6],
  ['BY-HM', 7],
])

export function regionsByRegistrationCode(regions: readonly Region[]) {
  return regions
    .map(region => ({ ...region, registrationCode: registrationCodes.get(region.code) }))
    .sort((left, right) =>
      (left.registrationCode ?? Number.MAX_SAFE_INTEGER) - (right.registrationCode ?? Number.MAX_SAFE_INTEGER),
    )
}
