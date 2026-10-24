import type { AnchorHTMLAttributes, MouseEvent, ReactNode } from 'react'
import { isStale, messageOf, type Issue, type Status } from './api'
import { AMBIGUOUS_NOTE, describeIssue, explainIssue, STATUS_LABEL } from './issues'
import { navigate } from './router'

const LEGAL_NOTICE = 'Informational only — not legal advice'

const INDIVIDUAL_PERMISSION = 'If you hold an individual permission, your limits may differ.'
import { BRAND } from './brand'

export function Link({ to, onClick, ...rest }: { to: string } & AnchorHTMLAttributes<HTMLAnchorElement>) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) {
      return
    }
    event.preventDefault()
    navigate(to)
  }
  return <a href={to} onClick={follow} {...rest} />
}

export function Screen({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <main className="screen">
      <header className="screen-header">
        <h1>{title}</h1>
        {action}
      </header>
      {children}
      <p className="footnote">
        <LegalNotice /> All times are Japan time.
      </p>
    </main>
  )
}

export function StatusBadge({ status }: { status: Status }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>
}

export function IssueList({ issues }: { issues: Issue[] }) {
  return (
    <>
      <ul className="issues">
        {issues.map((issue, index) => {
          const { title, detail } = describeIssue(issue)
          const explanation = explainIssue(issue)
          return (
            <li key={index} className={`issue issue-${issue.status.toLowerCase()}`}>
              <StatusBadge status={issue.status} />
              <div>
                <strong>{title}</strong>
                <p>{detail}</p>
                {explanation && <p className="explanation">{explanation}</p>}
              </div>
            </li>
          )
        })}
      </ul>
      {issues.some((issue) => issue.status === 'AMBIGUOUS') && <p className="muted small">{AMBIGUOUS_NOTE}</p>}
    </>
  )
}

export function Notice({ tone, children }: { tone: 'error' | 'info' | 'violation' | 'ambiguous'; children: ReactNode }) {
  return (
    <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function ErrorNotice({ error }: { error: unknown }) {
  return (
    <Notice tone="error">
      <p>{messageOf(error)}</p>
      {isStale(error) && (
        <button type="button" className="button button-small" onClick={() => window.location.reload()}>
          Reload
        </button>
      )}
    </Notice>
  )
}

export function LoadFailed({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <Notice tone="error">
      <p>{messageOf(error)}</p>
      <button type="button" className="button button-small" onClick={onRetry}>
        Try again
      </button>
    </Notice>
  )
}

export function Loading() {
  return <p className="muted">Loading…</p>
}

export function LegalNotice() {
  return (
    <>
      {LEGAL_NOTICE} —{' '}
      <a href={BRAND.officialRulesUrl} target="_blank" rel="noreferrer">
        official rules (ISA)
      </a>
      . {INDIVIDUAL_PERMISSION}
    </>
  )
}

export function LegalLinks() {
  return (
    <nav className="legal-links" aria-label="Legal">
      <Link to="/privacy">Privacy Policy</Link>
      <Link to="/terms">Terms of Service</Link>
    </nav>
  )
}

export function Disclaimer() {
  return (
    <section className="disclaimer">
      <h2>{LEGAL_NOTICE}</h2>
      <p>
        {BRAND.name} helps you plan part-time work within the limit for international students in Japan: 28 hours in any
        7 consecutive days across all jobs, or 8 hours a day and 40 hours in any 7 days during your school's long
        vacations. Where the rules are unclear it uses the stricter reading and marks the result as ambiguous. Pay
        figures are estimates.
      </p>
      <p>
        <LegalNotice />
      </p>
    </section>
  )
}
