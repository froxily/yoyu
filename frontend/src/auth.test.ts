import { describe, expect, it } from 'vitest'
import { ApiError, type User } from './api'
import { checkedProfile, describeInvalid, fieldError, formatCountdown, loginVerification, needsReauthentication, normalizeUsername, oauthStart, passwordSetup, reauthOptions, sanitizeCode, validUsername } from './auth'

const user: User = {
  id: 'account', email: 'a@example.com', fullName: 'A Person', username: 'person', emailVerified: true,
  hasPassword: true, recentlyAuthenticated: false, identities: [{ provider: 'google', linkedAt: '2026-01-01T00:00:00Z' }],
}

describe('username input', () => {
  it('lowercases without silently removing invalid characters', () => {
    expect(normalizeUsername('ABC_123')).toBe('abc_123')
    expect(normalizeUsername(' A.B ')).toBe(' a.b ')
  })
  it.each(['abc', '123', 'a_b', 'a'.repeat(20)])('accepts %s', (value) => expect(validUsername(value)).toBe(true))
  it.each(['', 'ab', 'a'.repeat(21), 'a-b', 'a.b', 'a b', 'éab', 'ABC', 'abc\n'])('rejects %s', (value) => expect(validUsername(value)).toBe(false))
})

describe('shared profile validation', () => {
  it('trims names and normalizes usernames before saving', () => {
    expect(checkedProfile({ fullName: '  A Person  ', username: 'A_PERSON' })).toEqual({ fullName: 'A Person', username: 'a_person' })
  })

  it('reports invalid names and usernames next to their fields', () => {
    let error: unknown
    try { checkedProfile({ fullName: '  ', username: 'a.b' }) } catch (failure) { error = failure }
    expect(error).toBeInstanceOf(ApiError)
    expect(fieldError(error, 'fullName')).toBe('Enter your full name.')
    expect(fieldError(error, 'username')).toBe('Use 3–20 letters, numbers or underscores.')
  })
})

describe('verification input and countdown', () => {
  it.each([['12 34-56', '123456'], ['a0b012345678', '001234'], ['１２３abc', ''], ['', '']])('sanitizes %s', (input, output) => {
    expect(sanitizeCode(input)).toBe(output)
  })
  it.each([[0, '0:00'], [9, '0:09'], [60, '1:00'], [125, '2:05'], [-3, '0:00'], [1.1, '0:02'], [NaN, '0:00'], [Infinity, '0:00']])('formats %s seconds', (seconds, formatted) => {
    expect(formatCountdown(seconds)).toBe(formatted)
  })
})

describe('password setup', () => {
  it('offers to change an existing password without needing a confirmed email', () => {
    expect(passwordSetup({ ...user, emailVerified: false })).toEqual({ label: 'Change password', needsEmail: false })
  })
  it('lets an account with a confirmed email set its first password', () => {
    expect(passwordSetup({ ...user, hasPassword: false })).toEqual({ label: 'Set password', needsEmail: false })
  })
  it.each([
    { email: null, emailVerified: false },
    { email: 'a@example.com', emailVerified: false },
  ])('asks for a confirmed email before a first password: %j', (email) => {
    expect(passwordSetup({ ...user, ...email, hasPassword: false })).toEqual({ label: 'Set password', needsEmail: true })
  })
})

describe('reauthentication availability', () => {
  it('offers password independently of provider availability', () => {
    expect(reauthOptions(user, { google: false, line: false, email: false })).toEqual({ password: true, email: false, google: false, line: false })
  })
  it('requires both enabled and linked social providers', () => {
    expect(reauthOptions(user, { google: true, line: true, email: true })).toEqual({ password: true, email: true, google: true, line: false })
    expect(reauthOptions({ ...user, identities: [{ provider: 'line', linkedAt: '' }] }, { google: true, line: true, email: false })).toEqual({ password: true, email: false, google: false, line: true })
  })
  it('requires verified email and supports no available methods', () => {
    expect(reauthOptions({ ...user, hasPassword: false, emailVerified: false, identities: [] }, { email: true, google: true, line: true }))
      .toEqual({ password: false, email: false, google: false, line: false })
  })
})

describe('auth problems', () => {
  it('rounds retry times up to minutes', () => {
    expect(describeInvalid(new ApiError(429, { retryAfterSeconds: 61 }))).toBe('Too many attempts. Try again in 2 minutes.')
    expect(describeInvalid(new ApiError(429, {}))).toBe('Too many attempts. Try again in 1 minute.')
  })
  it('keeps server field errors and finds password policy errors', () => {
    const error = new ApiError(400, { errors: [{ field: 'passwordWithinBcryptLimit', message: 'Password is too long.' }] })
    expect(fieldError(error, 'password')).toBe('Password is too long.')
    expect(fieldError(error, 'username')).toBeUndefined()
    expect(describeInvalid(error)).toBe('Password is too long.')
  })
  it('distinguishes email verification from sensitive-action confirmation', () => {
    const verification = new ApiError(403, { verificationRequired: true, verificationId: 'v', resendAfterSeconds: 40 })
    expect(loginVerification(verification)).toEqual({ verificationId: 'v', resendAfterSeconds: 40 })
    expect(needsReauthentication(verification)).toBe(false)
    const confirmation = new ApiError(403, { reauthenticate: true })
    expect(needsReauthentication(confirmation)).toBe(true)
    expect(loginVerification(confirmation)).toBeUndefined()
    expect(loginVerification(new ApiError(403, { verificationRequired: true }))).toBeUndefined()
    expect(needsReauthentication(new ApiError(400, { reauthenticate: true }))).toBe(false)
  })
})

describe('oauth start redirects', () => {
  it('only follows the server provider start paths', () => {
    expect(oauthStart('/oauth2/authorization/google')).toBe('/oauth2/authorization/google')
    expect(oauthStart('/oauth2/authorization/line')).toBe('/oauth2/authorization/line')
    for (const redirect of ['https://evil.example/oauth2/authorization/google', '//evil.example', '/oauth2/authorization/github', '/settings']) {
      expect(() => oauthStart(redirect)).toThrow()
    }
  })
})
