import { useMemo, useState, type FormEvent } from 'react'
import { api, ApiError, type CheckResult, type Job, type Shift, type ShiftDraft } from '../api'
import { useApi, useLiveCheck, type LiveCheck } from '../hooks'
import { goBack, useLocation } from '../router'
import { IdempotencyKeys } from '../idempotency'
import { toDraft, type ShiftFormValues } from '../shiftForm'
import { formatHours, isIsoDate, isPast, jstParts, minutesBetween, todayJst } from '../time'
import { ErrorNotice, IssueList, Link, LoadFailed, Loading, Notice, Screen, StatusBadge } from '../ui'
import { BRAND } from '../brand'

type Outcome =
  | { kind: 'confirm'; result: CheckResult }
  | { kind: 'saved'; result: CheckResult }
  | { kind: 'error'; error: unknown }

export default function ShiftScreen({ shiftId }: { shiftId?: string }) {
  const { query } = useLocation()
  const requested = query.get('date')
  const date = isIsoDate(requested) ? requested : todayJst()
  const title = shiftId ? 'Edit shift' : 'Add shift'
  const { data, error, reload } = useApi(
    () => Promise.all([api.jobs(true), shiftId ? api.shifts(date, date) : Promise.resolve([])]),
  )

  if (error) {
    return (
      <Screen title={title}>
        <LoadFailed error={error} onRetry={reload} />
      </Screen>
    )
  }
  if (!data) {
    return (
      <Screen title={title}>
        <Loading />
      </Screen>
    )
  }

  const [jobs, shifts] = data
  const shift = shifts.find((candidate) => candidate.id === shiftId)
  const choices = jobs.filter((job) => !job.archived || job.id === shift?.jobId)

  return (
    <Screen title={title}>
      {shiftId && !shift ? (
        <Notice tone="error">This shift could not be found. It may have been deleted.</Notice>
      ) : choices.length === 0 ? (
        <section className="card onboarding">
          <h2>Add a job first</h2>
          <p>Every shift belongs to a job, so {BRAND.name} can add up hours and estimate pay.</p>
          <Link to="/jobs?welcome" className="button button-primary">
            Add a job
          </Link>
        </section>
      ) : (
        <ShiftForm jobs={choices} shift={shift} date={date} />
      )}
    </Screen>
  )
}

function ShiftForm({ jobs, shift, date }: { jobs: Job[]; shift?: Shift; date: string }) {
  const [form, setForm] = useState<ShiftFormValues>(() => initialForm(jobs, shift, date))
  const [outcome, setOutcome] = useState<Outcome>()
  const [busy, setBusy] = useState(false)
  const [keys] = useState(() => new IdempotencyKeys())
  const { draft = null, error: formError } = useMemo(() => toDraft(form), [form])
  const check = useLiveCheck(draft, shift?.id)
  const saved = outcome?.kind === 'saved'

  function update(patch: Partial<ShiftFormValues>) {
    setForm((current) => ({ ...current, ...patch }))
    setOutcome(undefined)
  }

  async function save(confirmed: boolean) {
    if (draft === null) {
      return
    }
    const note = form.note.trim()
    const body = note ? { ...draft, note } : draft
    setBusy(true)
    setOutcome(undefined)
    try {
      const result = shift ? await api.updateShift(shift.id, body, confirmed, shift.version) : await api.createShift(body, confirmed, keys)
      if (result.compliance.status === 'OK') {
        goBack('/')
        return
      }
      setOutcome({ kind: 'saved', result: result.compliance })
    } catch (error) {
      if (error instanceof ApiError && error.status === 422 && error.problem.compliance) {
        setOutcome({ kind: 'confirm', result: error.problem.compliance })
      } else {
        setOutcome({ kind: 'error', error })
      }
    }
    setBusy(false)
  }

  async function remove() {
    if (!shift || !window.confirm('Delete this shift?')) {
      return
    }
    setBusy(true)
    try {
      await api.deleteShift(shift.id)
      goBack('/history')
    } catch (error) {
      setOutcome({ kind: 'error', error })
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void save(false)
  }

  return (
    <form className="stack" onSubmit={submit}>
      <fieldset className="card form" disabled={saved || busy}>
        <label className="field">
          <span>Job</span>
          <select value={form.jobId} onChange={(event) => update({ jobId: event.target.value })} required>
            {jobs.map((job) => (
              <option key={job.id} value={job.id}>
                {job.archived ? `${job.name} (archived)` : job.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Date</span>
          <input type="date" required value={form.date} onChange={(event) => update({ date: event.target.value })} />
        </label>
        <div className="field-row">
          <label className="field">
            <span>Start</span>
            <input type="time" required value={form.start} onChange={(event) => update({ start: event.target.value })} />
          </label>
          <label className="field">
            <span>End</span>
            <input type="time" required value={form.end} onChange={(event) => update({ end: event.target.value })} />
          </label>
        </div>
        <label className="toggle">
          <input type="checkbox" checked={form.hasBreak} onChange={(event) => update({ hasBreak: event.target.checked })} />
          <span>Break</span>
        </label>
        {form.hasBreak && (
          <div className="field-row">
            <label className="field">
              <span>Break start</span>
              <input
                type="time"
                required
                value={form.breakStart}
                onChange={(event) => update({ breakStart: event.target.value })}
              />
            </label>
            <label className="field">
              <span>Break end</span>
              <input
                type="time"
                required
                value={form.breakEnd}
                onChange={(event) => update({ breakEnd: event.target.value })}
              />
            </label>
          </div>
        )}
        <label className="field">
          <span>Note (optional)</span>
          <input type="text" maxLength={500} value={form.note} onChange={(event) => update({ note: event.target.value })} />
        </label>
        {formError ? (
          <p className="field-error" role="alert">
            {formError}
          </p>
        ) : (
          <p className="muted small">{summarize(draft)}</p>
        )}
      </fieldset>

      {!outcome || outcome.kind === 'error' ? <CheckPanel draft={draft} check={check} /> : null}

      {outcome?.kind === 'confirm' && (
        <section className={`banner banner-${outcome.result.status.toLowerCase()}`}>
          <h2>This future shift is not within your limits</h2>
          <IssueList issues={outcome.result.issues} />
          <p>Save it only if you are sure. You can still change or delete it later.</p>
          <div className="actions">
            <button type="button" className="button button-danger" disabled={busy} onClick={() => void save(true)}>
              Save anyway
            </button>
            <button type="button" className="button" disabled={busy} onClick={() => setOutcome(undefined)}>
              Change shift
            </button>
          </div>
        </section>
      )}

      {outcome?.kind === 'saved' && (
        <section className={`banner banner-${outcome.result.status.toLowerCase()}`}>
          <h2>Shift saved — it is not within your limits</h2>
          <IssueList issues={outcome.result.issues} />
          <button type="button" className="button button-primary" onClick={() => goBack('/')}>
            Done
          </button>
        </section>
      )}

      {outcome?.kind === 'error' && <ErrorNotice error={outcome.error} />}

      {!saved && outcome?.kind !== 'confirm' && (
        <div className="actions">
          <button type="submit" className="button button-primary" disabled={busy || draft === null}>
            {busy ? 'Saving…' : shift ? 'Save changes' : 'Save shift'}
          </button>
          <button type="button" className="button" disabled={busy} onClick={() => goBack('/')}>
            Cancel
          </button>
        </div>
      )}

      {shift && !saved && (
        <button type="button" className="button button-quiet-danger" disabled={busy} onClick={() => void remove()}>
          Delete shift
        </button>
      )}
    </form>
  )
}

function CheckPanel({ draft, check }: { draft: ShiftDraft | null; check: LiveCheck }) {
  if (draft === null) {
    return (
      <section className="card check">
        <p className="muted">Enter the date and times to check this shift against your limits.</p>
      </section>
    )
  }
  if (!check.result && !check.error) {
    return (
      <section className="card check" aria-busy="true">
        <p className="muted">Checking your limits…</p>
      </section>
    )
  }
  const result = check.result
  return (
    <section
      className={`card check${result ? ` check-${result.status.toLowerCase()}` : ''}${check.pending ? ' check-stale' : ''}`}
      aria-live="polite"
      aria-busy={check.pending}
    >
      {check.error ? (
        <Notice tone="error">{check.error}</Notice>
      ) : (
        result && (
          <>
            <div className="check-head">
              <StatusBadge status={result.status} />
              <strong>{headline(result)}</strong>
            </div>
            {result.issues.length > 0 && <IssueList issues={result.issues} />}
            {result.status !== 'OK' && <p className="muted small">{consequence(result, draft)}</p>}
          </>
        )
      )}
    </section>
  )
}

function headline(result: CheckResult): string {
  switch (result.status) {
    case 'OK':
      return 'Within your limits'
    case 'AMBIGUOUS':
      return 'Ambiguous — may be over the limit'
    case 'VIOLATION':
      return 'Over the limit'
  }
}

function consequence(result: CheckResult, draft: ShiftDraft): string {
  if (result.issues.some((issue) => issue.type === 'OVERLAP')) {
    return "Overlapping shifts can't be saved. Change the times or the other shift."
  }
  return isPast(draft.endAt)
    ? 'This shift has already happened, so it will be saved and the problem shown.'
    : 'This shift is in the future, so you will be asked to confirm before it is saved.'
}

function summarize(draft: ShiftDraft | null): string {
  if (draft === null) {
    return 'Times are Japan time (JST). An end time earlier than the start means the shift ends the next day.'
  }
  const breakMinutes = draft.breakStart && draft.breakEnd ? minutesBetween(draft.breakStart, draft.breakEnd) : 0
  const worked = minutesBetween(draft.startAt, draft.endAt) - breakMinutes
  const overnight = jstParts(draft.endAt).date !== jstParts(draft.startAt).date
  return `${formatHours(Math.max(0, worked))} worked${overnight ? ' · ends the next day' : ''} · Japan time (JST)`
}

function initialForm(jobs: Job[], shift: Shift | undefined, date: string): ShiftFormValues {
  if (!shift) {
    return {
      jobId: jobs.find((job) => !job.archived)?.id ?? jobs[0].id,
      date,
      start: '',
      end: '',
      hasBreak: false,
      breakStart: '',
      breakEnd: '',
      note: '',
    }
  }
  const start = jstParts(shift.startAt)
  return {
    jobId: shift.jobId,
    date: start.date,
    start: start.time,
    end: jstParts(shift.endAt).time,
    hasBreak: shift.breakStart !== null,
    breakStart: shift.breakStart ? jstParts(shift.breakStart).time : '',
    breakEnd: shift.breakEnd ? jstParts(shift.breakEnd).time : '',
    note: shift.note ?? '',
  }
}
