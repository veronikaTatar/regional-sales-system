import { expect, it } from 'vitest'
import { formatPlanMoney, normalizeMoney } from './planFormat'

it('принимает разделители разрядов и дополняет сумму копейками', () => {
  expect(normalizeMoney('65 000', 16)).toBe('65000.00')
  expect(normalizeMoney('65\u00a0000,00', 16)).toBe('65000.00')
  expect(normalizeMoney('40\u202f000.5', 16)).toBe('40000.50')
  expect(normalizeMoney('52,', 16)).toBe('52.00')
  expect(normalizeMoney(',5', 16)).toBe('0.50')
  expect(normalizeMoney(formatPlanMoney('9999999999999999.99'), 16)).toBe('9999999999999999.99')
})

it('отклоняет суммы с лишними знаками и неверной группировкой', () => {
  for (const value of ['', '-1', '5 00', '1 23 456', '6,123', '65,000.00', '10000000000000000']) {
    expect(normalizeMoney(value, 16)).toBeNull()
  }
})
