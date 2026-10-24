import { describe, expect, it } from 'vitest'
import type { Issue } from './api'
import { describeIssue, explainIssue } from './issues'

const window: Issue = {
  type: 'WINDOW',
  start: '2026-11-03',
  end: '2026-11-09',
  minutes: 1441,
  limitMinutes: 1440,
  status: 'VIOLATION',
  attribution: 'CALENDAR_SPLIT',
}

describe('describeIssue', () => {
  it('says when the rules changed during a window', () => {
    expect(describeIssue({ ...window, rulesChanged: true, ruleVersions: ['2000-01-01', '2026-11-09'] }).detail).toBe(
      'Limit 24h, counting hours split at midnight. Rules changed during these 7 days; the stricter limit is used.',
    )
  })

  it('says nothing about rule changes when one version applies', () => {
    expect(describeIssue(window).detail).toBe('Limit 24h, counting hours split at midnight.')
  })
})

describe('explainIssue', () => {
  it('names the shifts that put a window over its limit', () => {
    expect(
      explainIssue({
        ...window,
        start: '2026-11-06',
        end: '2026-11-12',
        minutes: 1740,
        limitMinutes: 1680,
        contributions: [
          { shiftId: 'a', jobId: 'j1', jobName: 'Cafe', startAt: '2026-11-06T10:00:00+09:00', minutes: 420, draft: false },
          { shiftId: 'b', jobId: 'j2', jobName: 'Shop', startAt: '2026-11-07T22:00:00+09:00', minutes: 360, draft: false },
          { jobId: 'j1', jobName: 'Cafe', startAt: '2026-11-10T10:00:00+09:00', minutes: 60, draft: true },
        ],
      }),
    ).toBe('Over 28h in the 7 days from Fri 6 Nov because Cafe Fri 10:00 (7h) + Shop Sat 22:00 (6h) + this shift (1h) fall in them.')
  })

  it('names the shifts that put a vacation day over its limit', () => {
    expect(
      explainIssue({
        type: 'DAY',
        start: '2026-12-23',
        end: '2026-12-23',
        minutes: 600,
        limitMinutes: 480,
        status: 'VIOLATION',
        attribution: 'CALENDAR_SPLIT',
        contributions: [
          { shiftId: 'a', jobId: 'j1', jobName: 'Cafe', startAt: '2026-12-22T20:00:00+09:00', minutes: 240, draft: false },
          { shiftId: 'b', jobId: 'j1', jobName: 'Cafe', startAt: '2026-12-23T09:00:00+09:00', minutes: 360, draft: false },
        ],
      }),
    ).toBe('Over 8h on Wed 23 Dec because Cafe Tue 20:00 (4h) + Cafe Wed 09:00 (6h) fall on it.')
  })

  it('explains nothing without contributions', () => {
    expect(explainIssue(window)).toBeUndefined()
  })
})
