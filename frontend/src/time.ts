const JST_OFFSET_MS = 9 * 60 * 60 * 1000
const DAY_MS = 24 * 60 * 60 * 1000
const MINUTE_MS = 60 * 1000

const dateFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' })
const longDateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
const monthFormat = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const weekdayFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: 'UTC' })

export function todayJst(nowMs: number = Date.now()): string {
  return new Date(nowMs + JST_OFFSET_MS).toISOString().slice(0, 10)
}

export function msUntilNextJstMidnight(nowMs: number): number {
  return DAY_MS - ((nowMs + JST_OFFSET_MS) % DAY_MS)
}

export function toIso(date: string, time: string): string {
  return `${date}T${time}:00+09:00`
}

export function jstParts(iso: string): { date: string; time: string } {
  const shifted = new Date(Date.parse(iso) + JST_OFFSET_MS).toISOString()
  return { date: shifted.slice(0, 10), time: shifted.slice(11, 16) }
}

export function minutesBetween(startIso: string, endIso: string): number {
  return Math.round((Date.parse(endIso) - Date.parse(startIso)) / MINUTE_MS)
}

export function isPast(iso: string): boolean {
  return Date.parse(iso) <= Date.now()
}

function utcMidnight(date: string): number {
  return Date.parse(`${date}T00:00:00Z`)
}

export function addDays(date: string, days: number): string {
  return new Date(utcMidnight(date) + days * DAY_MS).toISOString().slice(0, 10)
}

export function datesBetween(first: string, last: string): string[] {
  const dates: string[] = []
  for (let date = first; date <= last; date = addDays(date, 1)) {
    dates.push(date)
  }
  return dates
}

export function mondayOf(date: string): string {
  const weekday = new Date(utcMidnight(date)).getUTCDay()
  return addDays(date, -((weekday + 6) % 7))
}

export function isIsoDate(value: string | null): value is string {
  return value !== null && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(utcMidnight(value))
}

export function formatDate(date: string): string {
  return dateFormat.format(utcMidnight(date))
}

export function formatLongDate(date: string): string {
  return longDateFormat.format(utcMidnight(date))
}

export function formatWeekday(date: string): string {
  return weekdayFormat.format(utcMidnight(date))
}

export function formatRange(first: string, last: string): string {
  return first === last ? formatDate(first) : `${formatDate(first)} – ${formatDate(last)}`
}

export function monthOf(date: string): string {
  return date.slice(0, 7)
}

export function addMonths(month: string, months: number): string {
  const [year, index] = month.split('-').map(Number)
  const total = year * 12 + index - 1 + months
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`
}

export function monthRange(month: string): { from: string; to: string } {
  const from = `${month}-01`
  return { from, to: addDays(`${addMonths(month, 1)}-01`, -1) }
}

export function formatMonth(month: string): string {
  return monthFormat.format(utcMidnight(`${month}-01`))
}

export function formatHours(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${String(rest).padStart(2, '0')}m`
}

export function formatHoursCompact(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h${String(rest).padStart(2, '0')}`
}
