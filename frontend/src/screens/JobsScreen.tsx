import { useState, type FormEvent } from 'react'
import { api, messageOf, type Job } from '../api'
import { useApi } from '../hooks'
import { IdempotencyKeys } from '../idempotency'
import { useLocation } from '../router'
import { ErrorNotice, Link, LoadFailed, Loading, Notice, Screen } from '../ui'
import { BRAND } from '../brand'

const PALETTE: { color: string; name: string }[] = [
  { color: '#3b82f6', name: 'Blue' },
  { color: '#10b981', name: 'Green' },
  { color: '#8b5cf6', name: 'Violet' },
  { color: '#ec4899', name: 'Pink' },
  { color: '#14b8a6', name: 'Teal' },
  { color: '#0ea5e9', name: 'Sky' },
  { color: '#64748b', name: 'Slate' },
  { color: '#a16207', name: 'Brown' },
]

const yen = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'JPY' })

export default function JobsScreen() {
  const { query } = useLocation()
  const welcome = query.has('welcome')
  const { data: jobs, error, reload } = useApi(() => api.jobs(true))
  const [editing, setEditing] = useState<string | null>(null)
  const [added, setAdded] = useState(false)
  const [removeError, setRemoveError] = useState<string>()

  if (error) {
    return (
      <Screen title="Jobs">
        <LoadFailed error={error} onRetry={reload} />
      </Screen>
    )
  }
  if (!jobs) {
    return (
      <Screen title="Jobs">
        <Loading />
      </Screen>
    )
  }

  const active = jobs.filter((job) => !job.archived)
  const archived = jobs.filter((job) => job.archived)
  const creating = editing === 'new' || (active.length === 0 && !added)

  async function remove(job: Job) {
    if (
      !window.confirm(
        `Remove ${job.name}? If it has shifts it is archived and its shifts are kept; otherwise it is deleted.`,
      )
    ) {
      return
    }
    setRemoveError(undefined)
    try {
      await api.deleteJob(job.id)
      reload()
    } catch (failure) {
      setRemoveError(messageOf(failure))
    }
  }

  async function restore(job: Job) {
    setRemoveError(undefined)
    try {
      await api.restoreJob(job.id)
      reload()
    } catch (failure) {
      setRemoveError(messageOf(failure))
    }
  }

  function saved() {
    setEditing(null)
    reload()
  }

  function created() {
    setAdded(true)
    saved()
  }

  return (
    <Screen title="Jobs">
      {welcome && active.length === 0 && (
        <section className="card onboarding">
          <h2>Add your first job</h2>
          <p>
            Add each place you work part-time. The 28-hour limit counts all your jobs together, so {BRAND.name} needs every
            one of them. The wage is only used for pay estimates.
          </p>
        </section>
      )}
      {welcome && added && active.length > 0 && (
        <section className="card onboarding">
          <h2>Job added</h2>
          <p>
            Next, add a shift. If your school has a long vacation, add it too: the limit is different during long
            vacations.
          </p>
          <div className="actions">
            <Link to="/shifts/new" className="button button-primary">
              Add a shift
            </Link>
            <Link to="/vacations" className="button">
              Add a vacation
            </Link>
          </div>
        </section>
      )}

      {removeError && <Notice tone="error">{removeError}</Notice>}

      {active.length > 0 && (
        <ul className="list">
          {active.map((job) =>
            editing === job.id ? (
              <li key={job.id}>
                <JobForm job={job} onSaved={saved} onCancel={() => setEditing(null)} />
              </li>
            ) : (
              <li key={job.id} className="card row">
                <span className="dot" style={{ background: job.color }} aria-hidden="true" />
                <div className="row-main">
                  <strong>{job.name}</strong>
                  <span className="muted small">
                    {yen.format(job.hourlyWageYen)}/h
                    {job.transportYen > 0 && ` · transport ${yen.format(job.transportYen)}/shift`}
                  </span>
                </div>
                <div className="row-actions">
                  <button type="button" className="button button-small" onClick={() => setEditing(job.id)}>
                    Edit
                  </button>
                  <button type="button" className="button button-small" onClick={() => void remove(job)}>
                    Archive
                  </button>
                </div>
              </li>
            ),
          )}
        </ul>
      )}

      {creating ? (
        <JobForm onSaved={created} onCancel={active.length > 0 ? () => setEditing(null) : undefined} />
      ) : (
        <button type="button" className="button button-block" onClick={() => setEditing('new')}>
          Add a job
        </button>
      )}

      {archived.length > 0 && (
        <section className="card">
          <h2>Archived</h2>
          <ul className="plain">
            {archived.map((job) => (
              <li key={job.id} className="row">
                <span className="dot" style={{ background: job.color }} aria-hidden="true" />
                <span className="row-main">{job.name}</span>
                <button type="button" className="button button-small" onClick={() => void restore(job)}>
                  Restore
                </button>
              </li>
            ))}
          </ul>
          <p className="muted small">
            Archived jobs keep their shifts for history and pay estimates. Restore a job to choose it for new shifts
            again.
          </p>
        </section>
      )}
    </Screen>
  )
}

function JobForm({ job, onSaved, onCancel }: { job?: Job; onSaved: () => void; onCancel?: () => void }) {
  const [name, setName] = useState(job?.name ?? '')
  const [wage, setWage] = useState(job ? String(job.hourlyWageYen) : '')
  const [transport, setTransport] = useState(job ? String(job.transportYen) : '0')
  const [color, setColor] = useState(job?.color ?? PALETTE[0].color)
  const [keys] = useState(() => new IdempotencyKeys())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const palette = PALETTE.some((entry) => entry.color === color) ? PALETTE : [...PALETTE, { color, name: 'Current' }]

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      hourlyWageYen: Number(wage),
      transportYen: transport === '' ? 0 : Number(transport),
      color,
    }
    setBusy(true)
    setError(undefined)
    try {
      if (job) {
        await api.updateJob(job.id, input, job.version)
      } else {
        await api.createJob(input, keys)
      }
      onSaved()
    } catch (failure) {
      setError(failure)
      setBusy(false)
    }
  }

  return (
    <form className="card form" onSubmit={submit}>
      <h2>{job ? `Edit ${job.name}` : 'New job'}</h2>
      <label className="field">
        <span>Name</span>
        <input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      <label className="field">
        <span>Hourly wage (yen)</span>
        <input
          type="number"
          inputMode="numeric"
          min={1}
          step={1}
          required
          value={wage}
          onChange={(event) => setWage(event.target.value)}
        />
      </label>
      <label className="field">
        <span>Transport allowance per shift (yen)</span>
        <input
          type="number"
          inputMode="numeric"
          min={0}
          step={1}
          value={transport}
          onChange={(event) => setTransport(event.target.value)}
        />
      </label>
      <fieldset className="field">
        <legend>Colour</legend>
        <div className="swatches">
          {palette.map((entry) => (
            <label key={entry.color} className="swatch" style={{ background: entry.color }}>
              <input
                type="radio"
                name="color"
                value={entry.color}
                checked={color === entry.color}
                onChange={() => setColor(entry.color)}
                aria-label={entry.name}
              />
            </label>
          ))}
        </div>
      </fieldset>
      {error !== undefined && <ErrorNotice error={error} />}
      <div className="actions">
        <button type="submit" className="button button-primary" disabled={busy}>
          {job ? 'Save' : 'Add job'}
        </button>
        {onCancel && (
          <button type="button" className="button" disabled={busy} onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
