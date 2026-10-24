import { describe, expect, it } from 'vitest'
import { msUntilNextJstMidnight, todayJst } from './time'

describe('msUntilNextJstMidnight', () => {
  it('counts down to the next midnight in Japan', () => {
    expect(msUntilNextJstMidnight(Date.parse('2026-11-02T23:59:59+09:00'))).toBe(1000)
    expect(msUntilNextJstMidnight(Date.parse('2026-11-02T12:00:00+09:00'))).toBe(12 * 60 * 60 * 1000)
  })

  it('waits a whole day when it is exactly midnight in Japan', () => {
    expect(msUntilNextJstMidnight(Date.parse('2026-11-03T00:00:00+09:00'))).toBe(24 * 60 * 60 * 1000)
  })

  it('does not depend on the device time zone', () => {
    expect(msUntilNextJstMidnight(Date.parse('2026-11-02T04:59:59-10:00'))).toBe(1000)
  })
})

describe('todayJst', () => {
  it('reads the date in Japan for a given instant', () => {
    expect(todayJst(Date.parse('2026-11-02T15:00:00Z'))).toBe('2026-11-03')
    expect(todayJst(Date.parse('2026-11-02T14:59:59Z'))).toBe('2026-11-02')
  })
})
