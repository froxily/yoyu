import type { Attribution, Contribution, Issue, Status } from './api'
import { formatDate, formatHours, formatRange, formatWeekday, jstParts } from './time'
import { BRAND } from './brand'

export const STATUS_LABEL: Record<Status, string> = {
  OK: 'OK',
  AMBIGUOUS: 'Ambiguous',
  VIOLATION: 'Violation',
}

const ATTRIBUTION_LABEL: Record<Attribution, string> = {
  CALENDAR_SPLIT: 'counting hours split at midnight',
  START_DAY: 'counting overnight shifts on the day they start',
}

export const AMBIGUOUS_NOTE =
  `Ambiguous: these 7 days mix vacation and regular days, and the rules don't clearly say whether the 28 h or the 40 h limit applies. ${BRAND.name} flags it instead of guessing.`

export const RULES_CHANGED_NOTE = 'Rules changed during these 7 days; the stricter limit is used.'

export function describeIssue(issue: Issue): { title: string; detail: string } {
  const counted = issue.attribution ? `, ${ATTRIBUTION_LABEL[issue.attribution]}` : ''
  const rules = issue.rulesChanged ? ` ${RULES_CHANGED_NOTE}` : ''
  switch (issue.type) {
    case 'OVERLAP':
      return {
        title: `Overlaps another shift on ${formatRange(issue.start, issue.end)}`,
        detail: 'Shifts cannot overlap, across all your jobs.',
      }
    case 'DAY':
      return {
        title: `${formatDate(issue.start)}: ${formatHours(issue.minutes ?? 0)} on a vacation day`,
        detail: `Limit ${formatHours(issue.limitMinutes ?? 0)} per day during long vacations${counted}.`,
      }
    case 'WINDOW':
      return {
        title: `${formatRange(issue.start, issue.end)}: ${formatHours(issue.minutes ?? 0)} in 7 days`,
        detail:
          issue.status === 'AMBIGUOUS'
            ? `Over 28 h but within 40 h${counted}. Mixes vacation and regular days.${rules}`
            : `Limit ${formatHours(issue.limitMinutes ?? 0)}${counted}.${rules}`,
      }
  }
}

export function explainIssue(issue: Issue): string | undefined {
  if (!issue.contributions?.length || issue.type === 'OVERLAP') {
    return undefined
  }
  const shifts = issue.contributions.map(describeContribution).join(' + ')
  const over = `Over ${formatHours(issue.limitMinutes ?? 0)}`
  return issue.type === 'DAY'
    ? `${over} on ${formatDate(issue.start)} because ${shifts} fall on it.`
    : `${over} in the 7 days from ${formatDate(issue.start)} because ${shifts} fall in them.`
}

function describeContribution(contribution: Contribution): string {
  const hours = `(${formatHours(contribution.minutes)})`
  if (contribution.draft) {
    return `this shift ${hours}`
  }
  const { date, time } = jstParts(contribution.startAt)
  return `${contribution.jobName ?? 'A shift'} ${formatWeekday(date)} ${time} ${hours}`
}

export function worstStatus(statuses: Status[]): Status {
  if (statuses.includes('VIOLATION')) {
    return 'VIOLATION'
  }
  return statuses.includes('AMBIGUOUS') ? 'AMBIGUOUS' : 'OK'
}
