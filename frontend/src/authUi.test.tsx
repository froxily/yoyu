import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ApiError, type Providers, type User } from './api'
import { EmailChange, ReauthenticatePanel } from './accountAuth'
import { AuthField, CodeStep, PasswordField, ProfileForm, ProviderButtons } from './authUi'
import { BRAND } from './brand'
import { ForgotPasswordScreen, LoginScreen, RegisterScreen, WelcomeScreen } from './screens/AuthScreens'
import SettingsScreen from './screens/SettingsScreen'

const user: User = { id: 'a', email: null, fullName: 'A', username: 'abc', emailVerified: false, hasPassword: false, identities: [], recentlyAuthenticated: false }

describe('provider buttons', () => {
  it.each([
    { google: false, line: false, email: false },
    { google: true, line: false, email: false },
    { google: false, line: true, email: true },
    { google: true, line: true, email: true },
  ] satisfies Providers[])('only renders enabled providers: %j', (providers) => {
    const html = renderToStaticMarkup(<ProviderButtons providers={providers} />)
    for (const [provider, label] of [['google', 'Google'], ['line', 'LINE']] as const) {
      expect(html.includes(`href="/oauth2/authorization/${provider}"`)).toBe(providers[provider])
      expect(html.includes(`Continue with ${label}`)).toBe(providers[provider])
    }
    expect(html).not.toContain('<button')
  })
})

describe('authentication markup', () => {
  it('labels sign-in identifiers and passwords and uses centralized branding', () => {
    const html = renderToStaticMarkup(<LoginScreen onSignedIn={() => {}} />)
    expect(html).toContain('Email or username')
    expect(html).toContain('autoComplete="username"')
    expect(html).toContain('autoComplete="current-password"')
    expect(html).toContain('aria-pressed="false"')
    expect(html).toContain(BRAND.name)
    expect(html).toContain(BRAND.native)
    expect(html).toContain('aria-live="polite"')
  })

  it('renders a numeric six-digit code with an accessible countdown', () => {
    const html = renderToStaticMarkup(<CodeStep verification={{ verificationId: 'v', resendAfterSeconds: 65 }} email="a@example.com"
      onVerify={async () => {}} onRestart={() => {}} />)
    for (const expected of ['type="text"', 'inputMode="numeric"', 'autoComplete="one-time-code"', 'pattern="\\d{6}"', 'maxLength="6"', 'Resend code in 1:05', 'a@example.com', 'Check your spam folder.', 'disabled=""', 'aria-live="polite"']) {
      expect(html).toContain(expected)
    }
  })

  it('associates a field with its error and hint', () => {
    const html = renderToStaticMarkup(<AuthField label="Username" name="username" hint="Choose a username" error={new ApiError(409, { errors: [{ field: 'username', message: 'That username is taken.' }] })} />)
    expect(html).toContain('aria-invalid="true"')
    expect(html).toContain('aria-describedby=')
    expect(html).toContain('role="alert"')
    expect(html).toContain('That username is taken.')
  })

  it('offers a new-password hint and toggle', () => {
    const html = renderToStaticMarkup(<PasswordField newPassword value="" onChange={() => {}} />)
    expect(html).toContain('autoComplete="new-password"')
    expect(html).toContain('At least 10 characters. Avoid common passwords.')
    expect(html).toContain('aria-label="Show password"')
  })

  it('disables the password toggle along with a busy password field', () => {
    const html = renderToStaticMarkup(<PasswordField value="" onChange={() => {}} disabled />)
    expect(html).toMatch(/<input[^>]*disabled=""/)
    expect(html).toMatch(/<button[^>]*disabled=""/)
  })

  it('keeps profile values and labels in the shared editing and onboarding form', () => {
    const initial = { fullName: 'A Person', username: 'a_person' }
    const onboarding = renderToStaticMarkup(<ProfileForm initial={initial} onSave={async () => {}} />)
    const settings = renderToStaticMarkup(<ProfileForm initial={initial} title="Profile" submitLabel="Save" onSave={async () => {}} />)
    for (const html of [onboarding, settings]) {
      expect(html).toContain('value="A Person"')
      expect(html).toContain('value="a_person"')
      expect(html).toContain('autoComplete="name"')
      expect(html).toContain('autoComplete="username"')
      expect(html).toContain('3–20 letters, numbers or underscores')
    }
    expect(onboarding).toContain('>Continue</button>')
    expect(settings).toContain('<h2>Profile</h2>')
    expect(settings).toContain('>Save</button>')
  })

  it('associates a retried email-change error with the original email input', () => {
    const html = renderToStaticMarkup(<EmailChange initialEmail="a@example.com"
      error={new ApiError(400, { errors: [{ field: 'email', message: 'Enter a valid email address.' }] })}
      onRequest={async () => {}} onVerified={() => {}} onCancel={() => {}} onRestart={() => {}} />)
    expect(html).toContain('value="a@example.com"')
    expect(html).toContain('aria-invalid="true"')
    expect(html).toContain('aria-describedby=')
    expect(html).toContain('Enter a valid email address.')
  })

  it('blocks email editing while a confirmed account action is being retried', () => {
    const html = renderToStaticMarkup(<EmailChange disabled onRequest={async () => {}} onVerified={() => {}} onCancel={() => {}} onRestart={() => {}} />)
    expect(html).toContain('aria-busy="true"')
    expect(html).toMatch(/<input[^>]*disabled=""/)
    expect(html.match(/<button[^>]*disabled=""/g)).toHaveLength(2)
  })

  it('explains when identity cannot be confirmed', () => {
    const html = renderToStaticMarkup(<ReauthenticatePanel user={user} providers={{ email: true, google: true, line: true }} onConfirmed={async () => {}} onCancel={() => {}} onOAuth={() => {}} />)
    expect(html).toContain('Confirm it&#x27;s you')
    expect(html).toContain('This action can&#x27;t be confirmed right now.')
    expect(html).not.toContain('Email me a code')
    expect(html).not.toContain('Continue with Google')
  })

  it('offers only enabled and linked confirmation methods alongside password and verified email', () => {
    const html = renderToStaticMarkup(<ReauthenticatePanel user={{ ...user, hasPassword: true, emailVerified: true, email: 'a@example.com',
      identities: [{ provider: 'line', linkedAt: '2026-01-01T00:00:00Z' }] }} providers={{ email: true, google: true, line: true }}
      onConfirmed={async () => {}} onCancel={() => {}} onOAuth={() => {}} />)
    expect(html).toContain('Confirm with password')
    expect(html).toContain('Email me a code')
    expect(html).toContain('Continue with LINE')
    expect(html).not.toContain('Continue with Google')
    expect(html).not.toContain('/oauth2/authorization/')
  })

  it('offers to set a first password only once the account has a confirmed email', () => {
    const blocked = renderToStaticMarkup(<SettingsScreen user={user} onSignedOut={() => {}} />)
    expect(blocked).toMatch(/<button[^>]*disabled=""[^>]*>Set password<\/button>/)
    expect(blocked).toContain('A password needs a confirmed email address')

    const ready = renderToStaticMarkup(<SettingsScreen user={{ ...user, email: 'a@example.com', emailVerified: true }} onSignedOut={() => {}} />)
    expect(ready).toMatch(/<button type="button" class="button">Set password<\/button>/)
    expect(ready).not.toContain('A password needs a confirmed email address')

    const existing = renderToStaticMarkup(<SettingsScreen user={{ ...user, email: 'a@example.com', hasPassword: true }} onSignedOut={() => {}} />)
    expect(existing).toContain('>Change password</button>')
  })

  it('keeps the disclaimer on every auth screen, onboarding mode and Settings', () => {
    const screens = [<LoginScreen key="login" onSignedIn={() => {}} />, <RegisterScreen key="register" onSignedIn={() => {}} />, <ForgotPasswordScreen key="forgot" />,
      <WelcomeScreen key="social" onSignedIn={() => {}} />, <WelcomeScreen key="profile" user={user} onSignedIn={() => {}} />, <SettingsScreen key="settings" user={user} onSignedOut={() => {}} />]
    for (const screen of screens) {
      const html = renderToStaticMarkup(screen)
      expect(html).toContain('Informational only — not legal advice')
      expect(html).toContain(`href="${BRAND.officialRulesUrl}"`)
      expect(html).toContain('If you hold an individual permission, your limits may differ.')
    }
  })
})
