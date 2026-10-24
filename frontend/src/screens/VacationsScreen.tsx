import { useState, type FormEvent } from 'react'
import { api, messageOf, type Vacation } from '../api'
import { useApi } from '../hooks'
import { formatLongDate, todayJst } from '../time'
import { ErrorNotice, LoadFailed, Loading, Notice, Screen } from '../ui'
import { IdempotencyKeys } from '../idempotency'

export default function VacationsScreen() {
  const { data: vacations, error, reload } = useApi(() => api.vacations())
  const [editing, setEditing] = useState<string | null>(null)
  const [removeError, setRemoveError] = useState<string>()

  async function remove(vacation: Vacation) {
    if (!window.confirm(`Delete the vacation ${describe(vacation)}?`)) {
      return
    }
    setRemoveError(undefined)
    try {
      await api.deleteVacation(vacation.id)
      reload()
    } catch (failure) {
      setRemoveError(messageOf(failure))
    }
  }

  function saved() {
    setEditing(null)
    reload()
  }

  return (
    <Screen title="Long vacations">
      <p className="lead">
        During your school's long vacations the limit is 8 hours a day and 40 hours in any 7 days. Seven days that mix
        vacation and regular days are marked ambiguous above 28 hours, because the rules don't clearly say which limit
        applies.
      </p>
      {error ? (
        <LoadFailed error={error} onRetry={reload} />
      ) : !vacations ? (
        <Loading />
      ) : (
        <>
          {removeError && <Notice tone="error">{removeError}</Notice>}
          {vacations.length === 0 && editing !== 'new' && <p className="muted">No vacations yet.</p>}
          {vacations.length > 0 && (
            <ul className="list">
              {vacations.map((vacation) =>
                editing === vacation.id ? (
                  <li key={vacation.id}>
                    <VacationForm vacation={vacation} onSaved={saved} onCancel={() => setEditing(null)} />
                  </li>
                ) : (
                  <li key={vacation.id} className="card row">
                    <div className="row-main">
                      <strong>{vacation.label || 'Long vacation'}</strong>
                      <span className="muted small">{describe(vacation)}</span>
                    </div>
                    <div className="row-actions">
                      <button type="button" className="button button-small" onClick={() => setEditing(vacation.id)}>
                        Edit
                      </button>
                      <button type="button" className="button button-small" onClick={() => void remove(vacation)}>
                        Delete
                      </button>
                    </div>
                  </li>
                ),
              )}
            </ul>
          )}
          {editing === 'new' ? (
            <VacationForm onSaved={saved} onCancel={() => setEditing(null)} />
          ) : (
            <button type="button" className="button button-block" onClick={() => setEditing('new')}>
              Add a vacation
            </button>
          )}
        </>
      )}
    </Screen>
  )
}

function describe(vacation: Vacation): string {
  return `${formatLongDate(vacation.startDate)} – ${formatLongDate(vacation.endDate)}`
}

function VacationForm({
  vacation,
  onSaved,
  onCancel,
}: {
  vacation?: Vacation
  onSaved: () => void
  onCancel: () => void
}) {
  const [startDate, setStartDate] = useState(vacation?.startDate ?? todayJst())
  const [endDate, setEndDate] = useState(vacation?.endDate ?? '')
  const [label, setLabel] = useState(vacation?.label ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [keys] = useState(() => new IdempotencyKeys())

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = { startDate, endDate, label: label.trim() || null }
    setBusy(true)
    setError(undefined)
    try {
      if (vacation) {
        await api.updateVacation(vacation.id, input, vacation.version)
      } else {
        await api.createVacation(input, keys)
      }
      onSaved()
    } catch (failure) {
      setError(failure)
      setBusy(false)
    }
  }

  return (
    <form className="card form" onSubmit={submit}>
      <h2>{vacation ? 'Edit vacation' : 'New vacation'}</h2>
      <div className="field-row">
        <label className="field">
          <span>First day</span>
          <input type="date" required value={startDate} onChange={(event) => setStartDate(event.target.value)} />
        </label>
        <label className="field">
          <span>Last day</span>
          <input
            type="date"
            required
            min={startDate}
            value={endDate}
            onChange={(event) => setEndDate(event.target.value)}
          />
        </label>
      </div>
      <label className="field">
        <span>Name (optional)</span>
        <input
          maxLength={100}
          placeholder="Winter break"
          value={label}
          onChange={(event) => setLabel(event.target.value)}
        />
      </label>
      {error !== undefined && <ErrorNotice error={error} />}
      <div className="actions">
        <button type="submit" className="button button-primary" disabled={busy}>
          {vacation ? 'Save' : 'Add vacation'}
        </button>
        <button type="button" className="button" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
