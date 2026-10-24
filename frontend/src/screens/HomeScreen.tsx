import { api, type Job, type Overview } from '../api'
import { useApi, useJstToday, useRefreshOnVisible } from '../hooks'
import { worstStatus } from '../issues'
import { addDays, formatDate, formatHours, formatHoursCompact, formatWeekday } from '../time'
import { Disclaimer, IssueList, Link, LoadFailed, Loading, Screen } from '../ui'
import { BRAND } from '../brand'

const ISSUE_DAYS = 28

const BANNER_LIMIT = 3

export default function HomeScreen() {
  const today = useJstToday()

  return (
    <Screen title={BRAND.name}>
      <HomeData key={today} today={today} />
      <Disclaimer />
    </Screen>
  )
}

function HomeData({ today }: { today: string }) {
  const { data, error, reload } = useApi(
    () => Promise.all([api.compliance(today, addDays(today, ISSUE_DAYS - 1)), api.jobs()]),
  )
  useRefreshOnVisible(reload)

  if (error) {
    return <LoadFailed error={error} onRetry={reload} />
  }
  return data ? <HomeContent overview={data[0]} jobs={data[1]} today={today} /> : <Loading />
}

function HomeContent({ overview, jobs, today }: { overview: Overview; jobs: Job[]; today: string }) {
  const [todayCapacity, ...upcoming] = overview.capacity
  const todayTotal = overview.days.find((day) => day.date === today)
  const vacationDates = new Set(overview.days.filter((day) => day.vacation).map((day) => day.date))
  const status = worstStatus(overview.issues.map((issue) => issue.status))

  return (
    <>
      {jobs.length === 0 && (
        <section className="card onboarding">
          <h2>Welcome to {BRAND.name}</h2>
          <p>Add each job you work. {BRAND.name} adds up your hours across all of them.</p>
          <Link to="/jobs?welcome" className="button button-primary">
            Add your first job
          </Link>
        </section>
      )}

      {overview.issues.length > 0 && (
        <section className={`banner banner-${status.toLowerCase()}`}>
          <h2>{status === 'VIOLATION' ? 'Over the limit' : 'Ambiguous week ahead'}</h2>
          <IssueList issues={overview.issues.slice(0, BANNER_LIMIT)} />
          {overview.issues.length > BANNER_LIMIT && (
            <p>
              {overview.issues.length - BANNER_LIMIT} more. <Link to="/history">See History</Link>
            </p>
          )}
        </section>
      )}

      <section className="card hero">
        <p className="hero-label">Hours left today</p>
        <p className={`hero-number${todayCapacity.remainingMinutes === 0 ? ' hero-none' : ''}`}>
          {formatHours(todayCapacity.remainingMinutes)}
        </p>
        <p className="muted">
          {formatDate(today)} · worked {formatHours(todayTotal?.minutes ?? 0)}
          {todayTotal?.vacation && ' · vacation day'}
        </p>
      </section>

      <section className="card">
        <h2>Next 7 days</h2>
        <ol className="strip">
          {upcoming.map((day) => (
            <li
              key={day.date}
              className={`${day.remainingMinutes === 0 ? 'strip-none' : ''} ${vacationDates.has(day.date) ? 'strip-vacation' : ''}`}
            >
              <span>{formatWeekday(day.date)}</span>
              <span className="strip-date">{Number(day.date.slice(8))}</span>
              <strong>{formatHoursCompact(day.remainingMinutes)}</strong>
            </li>
          ))}
        </ol>
        <p className="muted small">
          Hours left on each day if you add no other shifts.
          {upcoming.some((day) => vacationDates.has(day.date)) && ' Underlined days are vacation days.'}
        </p>
      </section>

      <Link to="/shifts/new" className="button button-primary button-block">
        Add a shift
      </Link>
    </>
  )
}
