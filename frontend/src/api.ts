import type { IdempotencyKeys } from './idempotency'

export type Status = 'OK' | 'AMBIGUOUS' | 'VIOLATION'

export type Attribution = 'CALENDAR_SPLIT' | 'START_DAY'

export interface User {
  id: string
  email: string | null
  fullName: string | null
  username: string | null
  emailVerified: boolean
  hasPassword: boolean
  identities: { provider: Provider; linkedAt: string }[]
  recentlyAuthenticated: boolean
}

export type Provider = 'google' | 'line'

export interface Providers {
  google: boolean
  line: boolean
  email: boolean
}

export interface Verification {
  verificationId: string
  resendAfterSeconds?: number
}

export interface ProfileInput {
  fullName: string
  username: string
}

export interface RegisterInput extends ProfileInput {
  email: string
  password: string
}

export interface SocialPending {
  provider: Provider
  suggestedName: string | null
  email: string | null
  suggestedEmail: string | null
}

export interface Job {
  id: string
  name: string
  hourlyWageYen: number
  transportYen: number
  color: string
  archived: boolean
  version: number
}

export interface JobInput {
  name: string
  hourlyWageYen: number
  transportYen: number
  color: string
}

export interface Shift {
  id: string
  jobId: string
  startAt: string
  endAt: string
  breakStart: string | null
  breakEnd: string | null
  note: string | null
  workedMinutes: number
  version: number
}

export interface ShiftDraft {
  jobId: string
  startAt: string
  endAt: string
  breakStart?: string
  breakEnd?: string
  note?: string
}

export interface Contribution {
  shiftId?: string
  jobId: string
  jobName?: string
  startAt: string
  minutes: number
  draft: boolean
}

export interface Issue {
  type: 'WINDOW' | 'DAY' | 'OVERLAP'
  start: string
  end: string
  minutes?: number
  limitMinutes?: number
  status: Status
  attribution?: Attribution
  shiftId?: string
  rulesChanged?: boolean
  ruleVersions?: string[]
  contributions?: Contribution[]
}

export interface CheckResult {
  status: Status
  issues: Issue[]
}

export interface SavedShift {
  shift: Shift
  compliance: CheckResult
}

export interface DayTotal {
  date: string
  minutes: number
  vacation: boolean
  status: Status
}

export interface Overview {
  days: DayTotal[]
  issues: Issue[]
  capacity: { date: string; remainingMinutes: number }[]
}

export interface Vacation {
  id: string
  startDate: string
  endDate: string
  label: string | null
  version: number
}

export interface VacationInput {
  startDate: string
  endDate: string
  label: string | null
}

export interface PayMinutes {
  normal: number
  lateNight: number
  overtime: number
  overtimeLateNight: number
}

export interface JobPay {
  jobId: string
  name: string
  minutes: PayMinutes
  workPayYen: number
  transportYen: number
  totalYen: number
}

export interface PayReport {
  from: string
  to: string
  jobs: JobPay[]
  totalYen: number
}

export interface Problem {
  type?: string
  title?: string
  instance?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
  compliance?: CheckResult
  stale?: boolean
  verificationRequired?: boolean
  verificationId?: string
  resendAfterSeconds?: number
  reauthenticate?: boolean
  retryAfterSeconds?: number
  retryable?: boolean
}

export const OFFLINE = "Offline — hours can't be checked right now"

export const UNREACHABLE = "Can't reach the server — try again in a moment"

const UNEXPECTED = 'Something went wrong.'

const GATEWAY_ERRORS = [502, 503, 504]

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? UNEXPECTED)
    this.status = status
    this.problem = problem
  }
}

let unauthorized: () => void = () => {}

export function onUnauthorized(handler: () => void) {
  unauthorized = handler
}

function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))
  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice('XSRF-TOKEN='.length))
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  signal?: AbortSignal,
  keys?: IdempotencyKeys,
): Promise<T> {
  const unsafe = method !== 'GET'
  let response: Response
  try {
    if (unsafe && csrfToken() === undefined) {
      await fetch('/api/health', { credentials: 'same-origin', signal })
    }
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    if (unsafe) {
      headers['X-XSRF-TOKEN'] = csrfToken() ?? ''
    }
    if (keys) {
      headers['Idempotency-Key'] = keys.keyFor(JSON.stringify(body))
    }
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal,
    })
  } catch (error) {
    if (signal?.aborted) {
      throw error
    }
    throw new ApiError(0, { detail: navigator.onLine === false ? OFFLINE : UNREACHABLE })
  }
  if (response.status === 401 && path !== '/auth/login') {
    unauthorized()
  }
  if (GATEWAY_ERRORS.includes(response.status)) {
    const problem: Problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, { ...problem, status: response.status, detail: problem.detail ?? UNREACHABLE })
  }
  if (!response.ok) {
    const problem: Problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem)
  }
  keys?.reset()
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const api = {
  me: () => request<User>('GET', '/auth/me'),
  providers: () => request<Providers>('GET', '/auth/providers'),
  login: (identifier: string, password: string) => request<User>('POST', '/auth/login', { identifier, password }),
  register: (input: RegisterInput) => request<Verification>('POST', '/auth/register', input),
  verifyRegistration: (verificationId: string, code: string) =>
    request<User>('POST', '/auth/register/verify', { verificationId, code }),
  verifyLogin: (verificationId: string, code: string) =>
    request<User>('POST', '/auth/login/verify', { verificationId, code }),
  resendCode: (verificationId: string) => request<Verification>('POST', '/auth/codes/resend', { verificationId }),
  forgotPassword: (identifier: string) => request<Verification>('POST', '/auth/password/forgot', { identifier }),
  resetPassword: (verificationId: string, code: string, password: string) =>
    request<void>('POST', '/auth/password/reset', { verificationId, code, password }),
  socialPending: () => request<SocialPending>('GET', '/auth/social/pending'),
  completeSocial: (input: ProfileInput) => request<User>('POST', '/auth/social/complete', input),
  updateProfile: (input: ProfileInput) => request<User>('PUT', '/account/profile', input),
  changeEmail: (email: string) => request<Verification>('POST', '/account/email', { email }),
  verifyEmail: (verificationId: string, code: string) =>
    request<User>('POST', '/account/email/verify', { verificationId, code }),
  setPassword: (password: string) => request<User>('PUT', '/account/password', { password }),
  reauthenticate: (password: string) => request<void>('POST', '/auth/reauthenticate', { password }),
  reauthenticateEmail: () => request<Verification>('POST', '/auth/reauthenticate/email'),
  verifyReauthentication: (verificationId: string, code: string) =>
    request<void>('POST', '/auth/reauthenticate/email/verify', { verificationId, code }),
  linkIdentity: (provider: Provider) => request<{ redirect: string }>('POST', `/account/identities/${provider}/link`),
  reauthenticateIdentity: (provider: Provider) =>
    request<{ redirect: string }>('POST', `/account/identities/${provider}/reauthenticate`),
  unlinkIdentity: (provider: Provider) => request<void>('DELETE', `/account/identities/${provider}`),
  logout: () => request<void>('POST', '/auth/logout'),
  deleteAccount: () => request<void>('DELETE', '/account'),
  jobs: (includeArchived = false) => request<Job[]>('GET', `/jobs?includeArchived=${includeArchived}`),
  createJob: (job: JobInput, keys: IdempotencyKeys) => request<Job>('POST', '/jobs', job, undefined, keys),
  updateJob: (id: string, job: JobInput, version: number) => request<Job>('PUT', `/jobs/${id}`, { ...job, version }),
  deleteJob: (id: string) => request<void>('DELETE', `/jobs/${id}`),
  restoreJob: (id: string) => request<Job>('POST', `/jobs/${id}/restore`),
  shifts: (from: string, to: string) => request<Shift[]>('GET', `/shifts?from=${from}&to=${to}`),
  createShift: (draft: ShiftDraft, confirmed: boolean, keys: IdempotencyKeys) =>
    request<SavedShift>('POST', '/shifts', { ...draft, confirmed }, undefined, keys),
  updateShift: (id: string, draft: ShiftDraft, confirmed: boolean, version: number) =>
    request<SavedShift>('PUT', `/shifts/${id}`, { ...draft, confirmed, version }),
  deleteShift: (id: string) => request<void>('DELETE', `/shifts/${id}`),
  check: (draft: ShiftDraft, excludeShiftId: string | undefined, signal: AbortSignal) =>
    request<CheckResult>('POST', '/compliance/check', { ...draft, excludeShiftId }, signal),
  compliance: (from: string, to: string) => request<Overview>('GET', `/compliance?from=${from}&to=${to}`),
  vacations: () => request<Vacation[]>('GET', '/vacations'),
  createVacation: (vacation: VacationInput, keys: IdempotencyKeys) =>
    request<Vacation>('POST', '/vacations', vacation, undefined, keys),
  updateVacation: (id: string, vacation: VacationInput, version: number) =>
    request<Vacation>('PUT', `/vacations/${id}`, { ...vacation, version }),
  deleteVacation: (id: string) => request<void>('DELETE', `/vacations/${id}`),
  pay: (from: string, to: string) => request<PayReport>('GET', `/pay?from=${from}&to=${to}`),
}

export function isStale(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.problem.stale === true
}

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = error.problem.errors
    const message = fields?.length ? fields.map((field) => field.message).join('; ') : error.message
    return message.charAt(0).toUpperCase() + message.slice(1)
  }
  return UNEXPECTED
}
