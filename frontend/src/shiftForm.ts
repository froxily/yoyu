import type { ShiftDraft } from './api'
import { addDays, isIsoDate, toIso } from './time'

export interface ShiftFormValues {
  jobId: string
  date: string
  start: string
  end: string
  hasBreak: boolean
  breakStart: string
  breakEnd: string
  note: string
}

export interface FormDraft {
  draft?: ShiftDraft
  error?: string
}

export function toDraft(form: ShiftFormValues): FormDraft {
  if (!form.jobId || !isIsoDate(form.date) || !form.start || !form.end) {
    return {}
  }
  if (form.end === form.start) {
    return { error: 'End must be after start' }
  }
  const draft: ShiftDraft = {
    jobId: form.jobId,
    startAt: toIso(form.date, form.start),
    endAt: toIso(form.end > form.start ? form.date : addDays(form.date, 1), form.end),
  }
  if (!form.hasBreak) {
    return { draft }
  }
  if (!form.breakStart || !form.breakEnd) {
    return {}
  }
  if (form.breakEnd === form.breakStart) {
    return { error: 'Break end must be after break start' }
  }
  const breakDate = form.breakStart >= form.start ? form.date : addDays(form.date, 1)
  return {
    draft: {
      ...draft,
      breakStart: toIso(breakDate, form.breakStart),
      breakEnd: toIso(form.breakEnd > form.breakStart ? breakDate : addDays(breakDate, 1), form.breakEnd),
    },
  }
}
