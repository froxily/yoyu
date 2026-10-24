import { ApiError, messageOf, type ProfileInput, type Providers, type User, type Verification } from './api'

export const NO_PROVIDERS: Providers = { google: false, line: false, email: false }

export function normalizeUsername(value: string): string {
  return value.toLowerCase()
}

export function validUsername(value: string): boolean {
  return value.length >= 3 && value.length <= 20 && !/[^a-z0-9_]/.test(value)
}

export function checkedProfile(value: ProfileInput): ProfileInput {
  const input = { fullName: value.fullName.trim(), username: normalizeUsername(value.username) }
  const errors = []
  if (!input.fullName) errors.push({ field: 'fullName', message: 'Enter your full name.' })
  if (!validUsername(input.username)) errors.push({ field: 'username', message: 'Use 3–20 letters, numbers or underscores.' })
  if (errors.length) throw new ApiError(400, { errors })
  return input
}

export function oauthStart(redirect: string): string {
  if (!/^\/oauth2\/authorization\/(google|line)$/.test(redirect)) {
    throw new ApiError(0, { detail: 'Something went wrong.' })
  }
  return redirect
}

export function sanitizeCode(value: string): string {
  return value.replace(/\D/g, '').slice(0, 6)
}

export function countdownSeconds(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.ceil(value)) : 0
}

export function formatCountdown(value: number): string {
  const seconds = countdownSeconds(value)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

export function passwordSetup(user: User) {
  return {
    label: user.hasPassword ? 'Change password' : 'Set password',
    needsEmail: !user.hasPassword && (!user.email || !user.emailVerified),
  }
}

export function reauthOptions(user: User, providers: Providers) {
  return {
    password: user.hasPassword,
    email: user.emailVerified && providers.email,
    google: providers.google && user.identities.some((identity) => identity.provider === 'google'),
    line: providers.line && user.identities.some((identity) => identity.provider === 'line'),
  }
}

export function fieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError
    ? error.problem.errors?.find((entry) => entry.field === field || (field === 'password' && entry.field === 'passwordWithinBcryptLimit'))?.message
    : undefined
}

export function describeInvalid(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    const minutes = Math.max(1, Math.ceil((error.problem.retryAfterSeconds ?? 60) / 60))
    return `Too many attempts. Try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`
  }
  return messageOf(error)
}

export function loginVerification(error: unknown): Verification | undefined {
  if (error instanceof ApiError && error.status === 403 && error.problem.verificationRequired && error.problem.verificationId) {
    return { verificationId: error.problem.verificationId, resendAfterSeconds: error.problem.resendAfterSeconds }
  }
}

export function needsReauthentication(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403 && error.problem.reauthenticate === true
}
