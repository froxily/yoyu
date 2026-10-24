import { describe, expect, it } from 'vitest'
import { authMessages } from './authMessages'

describe('authentication query messages', () => {
  it.each([
    ['login', 'oauth', 'Sign-in could not be completed. Please try again.'],
    ['login', 'link-required', 'An account already uses this Google email. Sign in with your password, then link Google in Settings.'],
    ['login', 'rate-limited', 'Too many sign-in attempts from your network. Wait a few minutes and try again.'],
    ['settings', 'rate-limited', 'Too many sign-in attempts from your network. Wait a few minutes and try again.'],
    ['settings', 'identity-in-use', 'That Google or LINE account is already linked to another account. Try a different one.'],
    ['settings', 'provider-already-linked', 'You already have an account linked with that provider. Unlink it before linking another.'],
    ['settings', 'identity-mismatch', 'That is not the Google or LINE account linked here. Try again with your linked account.'],
    ['settings', 'reauth-not-fresh', "Google or LINE didn't confirm a fresh sign-in. Try again and enter your LINE email and password or scan its QR code (automatic login doesn't count), or use your password or an emailed code if you have one."],
    ['settings', 'reauth-google-no-time', "Google didn't share when you last signed in, so it can't confirm it's you here. Use your password or an emailed code instead."],
    ['settings', 'oauth', 'Google or LINE could not complete the request. Please try again.'],
  ] as const)('explains %s error %s', (page, code, text) => {
    expect(authMessages(page, new URLSearchParams({ error: code }))).toEqual([{ tone: 'error', text }])
  })

  it.each([
    ['linked', 'google', 'Google is now linked to your account.'],
    ['linked', 'line', 'LINE is now linked to your account.'],
    ['reauthenticated', 'google', 'Identity confirmed with Google.'],
    ['reauthenticated', 'line', 'Identity confirmed with LINE.'],
  ])('explains %s=%s', (key, value, text) => {
    expect(authMessages('settings', new URLSearchParams({ [key]: value }))).toEqual([{ tone: 'info', text }])
  })

  it('confirms password reset only on the login page', () => {
    const query = new URLSearchParams('reset=1')
    expect(authMessages('login', query)).toEqual([{ tone: 'info', text: 'Password changed. Sign in with your new password.' }])
    expect(authMessages('settings', query)).toEqual([])
  })

  it('ignores unknown, inherited and cross-page values', () => {
    for (const query of ['error=unknown', 'error=toString', 'linked=unknown', 'reauthenticated=unknown', 'reset=0']) {
      expect(authMessages('login', new URLSearchParams(query))).toEqual([])
      expect(authMessages('settings', new URLSearchParams(query))).toEqual([])
    }
    expect(authMessages('login', new URLSearchParams('error=identity-mismatch&linked=google'))).toEqual([])
    expect(authMessages('settings', new URLSearchParams('error=link-required'))).toEqual([])
  })
})
