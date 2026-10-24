import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, onUnauthorized, type Provider } from './api'

describe('authentication API contract', () => {
  beforeEach(() => vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=csrf' }))
  afterEach(() => { vi.unstubAllGlobals(); onUnauthorized(() => {}) })

  const verification = { verificationId: 'challenge', code: '123456' }
  const profile = { fullName: 'A Person', username: 'a_person' }
  const registration = { ...profile, email: 'a@example.com', password: 'a long password' }
  const cases: { name: string; method: string; path: string; body?: unknown; call: () => Promise<unknown> }[] = [
    { name: 'providers', method: 'GET', path: '/auth/providers', call: api.providers },
    { name: 'me', method: 'GET', path: '/auth/me', call: api.me },
    { name: 'login', method: 'POST', path: '/auth/login', body: { identifier: 'a_person', password: 'password' }, call: () => api.login('a_person', 'password') },
    { name: 'registration', method: 'POST', path: '/auth/register', body: registration, call: () => api.register(registration) },
    { name: 'registration code', method: 'POST', path: '/auth/register/verify', body: verification, call: () => api.verifyRegistration('challenge', '123456') },
    { name: 'login code', method: 'POST', path: '/auth/login/verify', body: verification, call: () => api.verifyLogin('challenge', '123456') },
    { name: 'resend', method: 'POST', path: '/auth/codes/resend', body: { verificationId: 'challenge' }, call: () => api.resendCode('challenge') },
    { name: 'forgot', method: 'POST', path: '/auth/password/forgot', body: { identifier: 'a_person' }, call: () => api.forgotPassword('a_person') },
    { name: 'reset', method: 'POST', path: '/auth/password/reset', body: { ...verification, password: 'new password' }, call: () => api.resetPassword('challenge', '123456', 'new password') },
    { name: 'pending', method: 'GET', path: '/auth/social/pending', call: api.socialPending },
    { name: 'complete social', method: 'POST', path: '/auth/social/complete', body: profile, call: () => api.completeSocial(profile) },
    { name: 'profile', method: 'PUT', path: '/account/profile', body: profile, call: () => api.updateProfile(profile) },
    { name: 'email', method: 'POST', path: '/account/email', body: { email: 'a@example.com' }, call: () => api.changeEmail('a@example.com') },
    { name: 'email code', method: 'POST', path: '/account/email/verify', body: verification, call: () => api.verifyEmail('challenge', '123456') },
    { name: 'confirm password', method: 'POST', path: '/auth/reauthenticate', body: { password: 'password' }, call: () => api.reauthenticate('password') },
    { name: 'confirm email', method: 'POST', path: '/auth/reauthenticate/email', call: api.reauthenticateEmail },
    { name: 'confirm email code', method: 'POST', path: '/auth/reauthenticate/email/verify', body: verification, call: () => api.verifyReauthentication('challenge', '123456') },
    { name: 'delete account', method: 'DELETE', path: '/account', call: api.deleteAccount },
    ...(['google', 'line'] satisfies Provider[]).flatMap((provider) => [
      { name: `link ${provider}`, method: 'POST', path: `/account/identities/${provider}/link`, call: () => api.linkIdentity(provider) },
      { name: `confirm ${provider}`, method: 'POST', path: `/account/identities/${provider}/reauthenticate`, call: () => api.reauthenticateIdentity(provider) },
      { name: `unlink ${provider}`, method: 'DELETE', path: `/account/identities/${provider}`, call: () => api.unlinkIdentity(provider) },
    ]),
  ]

  it.each(cases)('sends $name with credentials and the contracted payload', async ({ method, path, body, call }) => {
    const fetch = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    await call()
    expect(fetch).toHaveBeenCalledExactlyOnceWith(`/api${path}`, expect.objectContaining({
      method, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body),
      headers: expect.objectContaining(method === 'GET' ? { Accept: 'application/json' } : { 'X-XSRF-TOKEN': 'csrf' }),
    }))
  })

  it('does not treat bad login credentials as an expired session', async () => {
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 401 })))
    await expect(api.login('user', 'wrong')).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).not.toHaveBeenCalled()
    await expect(api.me()).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it.each([
    [403, { verificationRequired: true, verificationId: 'v', resendAfterSeconds: 60 }],
    [403, { reauthenticate: true }],
    [429, { retryAfterSeconds: 120 }],
    [503, { retryable: true, detail: 'Email is temporarily unavailable.' }],
    [409, { errors: [{ field: 'username', message: 'That username is taken.' }] }],
  ])('preserves %s problem details', async (status, problem) => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(problem), { status })))
    const error = await api.me().catch((failure: unknown) => failure)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status, problem })
  })

  it('handles empty success responses and wrong confirmation passwords without signing out', async () => {
    const unauthorized = vi.fn()
    onUnauthorized(unauthorized)
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
    await expect(api.resetPassword('v', '123456', 'password')).resolves.toBeUndefined()
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ errors: [{ field: 'password', message: 'Wrong password.' }] }), { status: 400 })))
    await expect(api.reauthenticate('wrong')).rejects.toMatchObject({ status: 400 })
    expect(unauthorized).not.toHaveBeenCalled()
  })
})
