import type { Provider } from './api'

export const PROVIDER_NAMES: Record<Provider, string> = { google: 'Google', line: 'LINE' }

export const LOGIN_ERRORS: Record<string, string> = {
  oauth: 'Sign-in could not be completed. Please try again.',
  'link-required': 'An account already uses this Google email. Sign in with your password, then link Google in Settings.',
  'rate-limited': 'Too many sign-in attempts from your network. Wait a few minutes and try again.',
}

export const SETTINGS_ERRORS: Record<string, string> = {
  'identity-in-use': 'That Google or LINE account is already linked to another account. Try a different one.',
  'provider-already-linked': 'You already have an account linked with that provider. Unlink it before linking another.',
  'identity-mismatch': 'That is not the Google or LINE account linked here. Try again with your linked account.',
  'reauth-not-fresh': "Google or LINE didn't confirm a fresh sign-in. Try again and enter your LINE email and password or scan its QR code (automatic login doesn't count), or use your password or an emailed code if you have one.",
  'rate-limited': 'Too many sign-in attempts from your network. Wait a few minutes and try again.',
  'reauth-google-no-time': "Google didn't share when you last signed in, so it can't confirm it's you here. Use your password or an emailed code instead.",
  oauth: 'Google or LINE could not complete the request. Please try again.',
}

export interface AuthMessage {
  tone: 'error' | 'info'
  text: string
}

export function authMessages(page: 'login' | 'settings', query: URLSearchParams): AuthMessage[] {
  const messages: AuthMessage[] = []
  const error = query.get('error')
  const errors = page === 'login' ? LOGIN_ERRORS : SETTINGS_ERRORS
  if (error && Object.hasOwn(errors, error)) messages.push({ tone: 'error', text: errors[error] })
  if (page === 'login' && query.get('reset') === '1') {
    messages.push({ tone: 'info', text: 'Password changed. Sign in with your new password.' })
  }
  if (page === 'settings') {
    for (const key of ['linked', 'reauthenticated'] as const) {
      const provider = query.get(key)
      if (provider === 'google' || provider === 'line') {
        messages.push({ tone: 'info', text: key === 'linked'
          ? `${PROVIDER_NAMES[provider]} is now linked to your account.`
          : `Identity confirmed with ${PROVIDER_NAMES[provider]}.` })
      }
    }
  }
  return messages
}
