import { api, type DayTotal, type Job, type Overview, type Shift } from '../api'
import { useApi } from '../hooks'
import { navigate, useLocation } from '../router'
import {
  addDays,
  datesBetween,
  formatDate,
  formatHours,
  formatRange,
  isIsoDate,
  jstParts,
  mondayOf,
  todayJst,
} from '../time'
import { IssueList, Link, LoadFailed, Loading, Screen, StatusBadge } from '../ui'

const WEEKS = 4

export default function HistoryScreen() {
  const { query } = useLocation()
  const requested = query.get('week')
  const lastWeek = isIsoDate(requested) ? mondayOf(requested) : addDays(mondayOf(todayJst()), 7)
  const from = addDays(lastWeek, -7 * (WEEKS - 1))
  const to = addDays(lastWeek, 6)
  const { data, error, reload } = useApi(
    () => Promise.all([api.shifts(from, to), api.compliance(from, to), api.jobs(true)]),
  )

  function page(weeks: number) {
    navigate(`/history?week=${addDays(lastWeek, 7 * weeks)}`)
  }

  return (
    <Screen title="History">
      <div className="pager">
        <button type="button" className="button button-small" onClick={() => page(-WEEKS)}>
          ‹ Earlier
        </button>
        <span>{formatRange(from, to)}</span>
        <button type="button" className="button button-small" onClick={() => page(WEEKS)}>
          Later ›
        </button>
      </div>
      {error ? (
        <LoadFailed error={error} onRetry={reload} />
      ) : data ? (
        <HistoryContent shifts={data[0]} overview={data[1]} jobs={data[2]} lastWeek={lastWeek} />
      ) : (
        <Loading />
      )}
    </Screen>
  )
}

function HistoryContent({
  shifts,
  overview,
  jobs,
  lastWeek,
}: {
  shifts: Shift[]
  overview: Overview
  jobs: Job[]
  lastWeek: string
}) {
  const jobsById = new Map(jobs.map((job) => [job.id, job]))
  const days = new Map(overview.days.map((day) => [day.date, day]))
  const shiftsByDate = new Map<string, Shift[]>()
  for (const shift of shifts) {
    const date = jstParts(shift.startAt).date
    shiftsByDate.set(date, [...(shiftsByDate.get(date) ?? []), shift])
  }
  const weeks = Array.from({ length: WEEKS }, (_, index) => addDays(lastWeek, -7 * index))

  return (
    <>
      {overview.issues.length > 0 && (
        <section className="card">
          <h2>Issues in these weeks</h2>
          <IssueList issues={overview.issues} />
        </section>
      )}
      {weeks.map((monday) => {
        const dates = datesBetween(monday, addDays(monday, 6)).filter(
          (date) => (days.get(date)?.minutes ?? 0) > 0 || shiftsByDate.has(date),
        )
        return (
          <section key={monday} className="card week">
            <h2>{formatRange(monday, addDays(monday, 6))}</h2>
            {dates.length === 0 ? (
              <p className="muted">No shifts.</p>
            ) : (
              <ul className="days">
                {dates.map((date) => (
                  <DayRow
                    key={date}
                    date={date}
                    total={days.get(date)}
                    shifts={shiftsByDate.get(date) ?? []}
                    jobsById={jobsById}
                  />
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </>
  )
}

function DayRow({
  date,
  total,
  shifts,
  jobsById,
}: {
  date: string
  total?: DayTotal
  shifts: Shift[]
  jobsById: Map<string, Job>
}) {
  const status = total?.status ?? 'OK'
  return (
    <li className={`day day-${status.toLowerCase()}`}>
      <div className="day-head">
        <strong>{formatDate(date)}</strong>
        <span className="day-total">{formatHours(total?.minutes ?? 0)}</span>
      </div>
      {(status !== 'OK' || total?.vacation) && (
        <div className="day-tags">
          {status !== 'OK' && <StatusBadge status={status} />}
          {total?.vacation && <span className="tag">Vacation day</span>}
        </div>
      )}
      {shifts.length === 0 ? (
        <p className="muted small">Continues from the previous day's shift.</p>
      ) : (
        <ul className="shifts">
          {shifts.map((shift) => {
            const job = jobsById.get(shift.jobId)
            return (
              <li key={shift.id}>
                <Link to={`/shifts/${shift.id}/edit?date=${date}`} className="shift">
                  <span className="dot" style={{ background: job?.color }} aria-hidden="true" />
                  <span className="shift-main">
                    <span>{job?.name ?? 'Job'}</span>
                    <span className="muted small">{describeTimes(shift)}</span>
                  </span>
                  <span className="shift-hours">{formatHours(shift.workedMinutes)}</span>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </li>
  )
}

function describeTimes(shift: Shift): string {
  const start = jstParts(shift.startAt)
  const end = jstParts(shift.endAt)
  const times = `${start.time}–${end.time}${end.date !== start.date ? ' (next day)' : ''}`
  if (!shift.breakStart || !shift.breakEnd) {
    return times
  }
  return `${times}, break ${jstParts(shift.breakStart).time}–${jstParts(shift.breakEnd).time}`
}
