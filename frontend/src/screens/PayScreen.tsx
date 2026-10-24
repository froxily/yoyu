import { api, type JobPay, type PayMinutes } from '../api'
import { useApi } from '../hooks'
import { navigate, useLocation } from '../router'
import { addMonths, formatHours, formatMonth, monthOf, monthRange, todayJst } from '../time'
import { LoadFailed, Loading, Screen } from '../ui'

const yen = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'JPY' })

const CATEGORIES: { key: keyof PayMinutes; label: string; multiplier: string }[] = [
  { key: 'normal', label: 'Normal', multiplier: '×1.00' },
  { key: 'lateNight', label: 'Late night (22:00–05:00)', multiplier: '×1.25' },
  { key: 'overtime', label: 'Overtime (over 8 h a day per job)', multiplier: '×1.25' },
  { key: 'overtimeLateNight', label: 'Overtime late at night', multiplier: '×1.50' },
]

export default function PayScreen() {
  const { query } = useLocation()
  const requested = query.get('month')
  const month = requested !== null && /^\d{4}-(0[1-9]|1[0-2])$/.test(requested) ? requested : monthOf(todayJst())
  const { from, to } = monthRange(month)
  const { data: report, error, reload } = useApi(() => api.pay(from, to))

  return (
    <Screen title="Pay estimate">
      <div className="pager">
        <button
          type="button"
          className="button button-small"
          onClick={() => navigate(`/pay?month=${addMonths(month, -1)}`)}
        >
          ‹ Previous
        </button>
        <span>{formatMonth(month)}</span>
        <button type="button" className="button button-small" onClick={() => navigate(`/pay?month=${addMonths(month, 1)}`)}>
          Next ›
        </button>
      </div>
      {error ? (
        <LoadFailed error={error} onRetry={reload} />
      ) : !report ? (
        <Loading />
      ) : (
        <>
          <section className="card hero">
            <p className="hero-label">Estimated pay</p>
            <p className="hero-number">{yen.format(report.totalYen)}</p>
            <p className="muted">Estimate for shifts starting in {formatMonth(month)}</p>
          </section>
          {report.jobs.length === 0 ? (
            <p className="muted">No shifts this month.</p>
          ) : (
            report.jobs.map((job) => <JobPayCard key={job.jobId} job={job} />)
          )}
          <section className="card">
            <h2>Not included</h2>
            <ul className="bullets">
              <li>Legal holiday premium</li>
              <li>The 50% premium for overtime beyond 60 hours a month</li>
              <li>
                Overtime from combining hours across different employers (labour law adds them up, Labour Standards Act
                Article 38)
              </li>
              <li>Taxes and deductions</li>
            </ul>
          </section>
        </>
      )}
    </Screen>
  )
}

function JobPayCard({ job }: { job: JobPay }) {
  return (
    <section className="card">
      <div className="pay-head">
        <h2>{job.name}</h2>
        <strong>
          {yen.format(job.totalYen)} <span className="muted small">estimate</span>
        </strong>
      </div>
      <table className="pay-table">
        <tbody>
          {CATEGORIES.filter((category) => job.minutes[category.key] > 0).map((category) => (
            <tr key={category.key}>
              <th scope="row">
                {category.label} <span className="muted">{category.multiplier}</span>
              </th>
              <td>{formatHours(job.minutes[category.key])}</td>
            </tr>
          ))}
          <tr>
            <th scope="row">Work pay (estimate)</th>
            <td>{yen.format(job.workPayYen)}</td>
          </tr>
          <tr>
            <th scope="row">Transport</th>
            <td>{yen.format(job.transportYen)}</td>
          </tr>
        </tbody>
      </table>
    </section>
  )
}
