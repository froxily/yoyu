import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type Providers, type SocialPending, type User } from './api'
import { ForgotPasswordScreen, LoginScreen, RegisterScreen, WelcomeScreen } from './screens/AuthScreens'
import SettingsScreen from './screens/SettingsScreen'

const state = vi.hoisted(() => ({
  providers: { google: true, line: true, email: true } as Providers,
  pending: { provider: 'line', suggestedName: 'A Person', email: null, suggestedEmail: 'suggested@example.com' } as SocialPending,
  error: undefined as unknown,
}))

vi.mock('./hooks', () => ({ useApi: (loader: unknown) => loader === api.providers
  ? { data: state.providers, reload: () => {} }
  : { data: state.pending, error: state.error, reload: () => {} } }))

const account: User = { id: 'a', fullName: 'A Person', username: 'a_person', email: 'a@example.com', hasPassword: true,
  emailVerified: true, recentlyAuthenticated: true, identities: [{ provider: 'google', linkedAt: '2026-01-01T00:00:00Z' }] }

describe('provider-aware screens', () => {
  beforeEach(() => { state.providers = { google: true, line: true, email: true }; state.error = undefined })

  it('offers email registration and recovery only when email is enabled', () => {
    const register = () => renderToStaticMarkup(<RegisterScreen onSignedIn={() => {}} />)
    const login = () => renderToStaticMarkup(<LoginScreen onSignedIn={() => {}} />)
    const forgot = () => renderToStaticMarkup(<ForgotPasswordScreen />)
    expect(register()).toContain('autoComplete="email"')
    expect(register()).toContain('3–20 letters, numbers or underscores')
    expect(login()).toContain('Forgot password?')
    expect(forgot()).toContain('Send reset code')
    state.providers.email = false
    expect(register()).not.toContain('autoComplete="email"')
    expect(register()).toContain('Email sign-up isn&#x27;t available yet')
    expect(login()).not.toContain('Forgot password?')
    expect(forgot()).not.toContain('Send reset code')
  })

  it('shows a neutral message if all registration methods are disabled', () => {
    state.providers = { google: false, line: false, email: false }
    const html = renderToStaticMarkup(<RegisterScreen onSignedIn={() => {}} />)
    expect(html).toContain('New accounts aren&#x27;t available right now.')
    expect(html).not.toContain('/oauth2/authorization/')
    expect(html).not.toContain('<form')
  })

  it('does not adopt the unverified suggested email during social completion', () => {
    const html = renderToStaticMarkup(<WelcomeScreen onSignedIn={() => {}} />)
    expect(html).toContain('value="A Person"')
    expect(html).not.toContain('suggested@example.com')
    expect(html).not.toContain('Your verified email:')
  })

  it('explains an expired social sign-up', () => {
    state.error = new ApiError(404, {})
    const html = renderToStaticMarkup(<WelcomeScreen onSignedIn={() => {}} />)
    expect(html).toContain('This sign-up expired')
    expect(html).toContain('href="/login"')
  })

  it('preserves Settings sections and shows enabled sign-in methods and verified email', () => {
    const render = () => renderToStaticMarkup(<SettingsScreen user={account} onSignedOut={() => {}} />)
    const html = render()
    for (const text of ['Setup', 'Profile', 'Sources', 'Account', 'Delete account', 'Sign out', 'Sign-in methods', 'Verified', 'Unlink Google', 'Link LINE', 'dateTime="2026-01-01T00:00:00Z"']) {
      expect(html).toContain(text)
    }
    state.providers = { google: false, line: false, email: false }
    expect(render()).not.toContain('Change email')
    expect(render()).not.toContain('Unlink Google')
    expect(render()).not.toContain('Link LINE')
  })
})
