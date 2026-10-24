import { describe, expect, it } from 'vitest'
import { toDraft, type ShiftFormValues } from './shiftForm'

const form: ShiftFormValues = {
  jobId: 'job',
  date: '2026-11-02',
  start: '10:00',
  end: '17:00',
  hasBreak: false,
  breakStart: '',
  breakEnd: '',
  note: '',
}

describe('toDraft', () => {
  it('builds a same-day shift in Japan time', () => {
    expect(toDraft(form)).toEqual({
      draft: { jobId: 'job', startAt: '2026-11-02T10:00:00+09:00', endAt: '2026-11-02T17:00:00+09:00' },
    })
  })

  it('rejects an end time equal to the start time instead of making a 24-hour shift', () => {
    expect(toDraft({ ...form, start: '10:00', end: '10:00' })).toEqual({ error: 'End must be after start' })
  })

  it('treats an end time before the start time as the next day', () => {
    expect(toDraft({ ...form, start: '22:00', end: '03:00' })).toEqual({
      draft: { jobId: 'job', startAt: '2026-11-02T22:00:00+09:00', endAt: '2026-11-03T03:00:00+09:00' },
    })
    expect(toDraft({ ...form, start: '10:00', end: '09:59' })).toEqual({
      draft: { jobId: 'job', startAt: '2026-11-02T10:00:00+09:00', endAt: '2026-11-03T09:59:00+09:00' },
    })
  })

  it('rejects a break whose end equals its start', () => {
    expect(toDraft({ ...form, hasBreak: true, breakStart: '13:00', breakEnd: '13:00' })).toEqual({
      error: 'Break end must be after break start',
    })
  })

  it('places a break after midnight on the next day', () => {
    expect(
      toDraft({ ...form, start: '20:00', end: '04:00', hasBreak: true, breakStart: '23:30', breakEnd: '00:30' }),
    ).toEqual({
      draft: {
        jobId: 'job',
        startAt: '2026-11-02T20:00:00+09:00',
        endAt: '2026-11-03T04:00:00+09:00',
        breakStart: '2026-11-02T23:30:00+09:00',
        breakEnd: '2026-11-03T00:30:00+09:00',
      },
    })
  })

  it('returns nothing until the required fields are filled', () => {
    expect(toDraft({ ...form, end: '' })).toEqual({})
    expect(toDraft({ ...form, hasBreak: true, breakStart: '13:00' })).toEqual({})
  })
})
