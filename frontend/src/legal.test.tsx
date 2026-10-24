import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { api, type Providers, type SocialPending, type User } from './api'
import { BRAND } from './brand'
import { legalScreen, PUBLIC_PATHS, signedOutScreen } from './publicRoutes'
import { ForgotPasswordScreen, LandingScreen, LoginScreen, RegisterScreen, WelcomeScreen } from './screens/AuthScreens'
import { LEGAL_UPDATED, PrivacyScreen, TermsScreen } from './screens/LegalScreens'
import SettingsScreen from './screens/SettingsScreen'

const state = vi.hoisted(() => ({
  providers: { google: true, line: true, email: true } as Providers,
  pending: { provider: 'google', suggestedName: 'A Person', email: 'a@example.com', suggestedEmail: null } as SocialPending,
}))

vi.mock('./hooks', () => ({ useApi: (loader: unknown) => loader === api.providers
  ? { data: state.providers, reload: () => {} }
  : { data: state.pending, reload: () => {} } }))

const account: User = { id: 'a', fullName: 'A Person', username: 'a_person', email: 'a@example.com', hasPassword: true,
  emailVerified: true, recentlyAuthenticated: false, identities: [] }

const signIn = () => Promise.resolve()

function text(html: string) {
  return html.replace(/<[^>]+>/g, '').replace(/&#x27;/g, "'").replace(/\s+/g, ' ')
}

describe('public routes', () => {
  it('gives every public path its own screen, and anything else asks to sign in', () => {
    for (const path of PUBLIC_PATHS.filter((entry) => entry !== '/login')) {
      expect(signedOutScreen(path, path, signIn).type, path).not.toBe(LoginScreen)
    }
    expect(signedOutScreen('/', '/', signIn).type).toBe(LandingScreen)
    expect(signedOutScreen('/privacy', '/privacy', signIn).type).toBe(PrivacyScreen)
    expect(signedOutScreen('/terms', '/terms', signIn).type).toBe(TermsScreen)
    expect(signedOutScreen('/history', '/history', signIn).type).toBe(LoginScreen)
  })

  it('serves the legal pages without an account', () => {
    expect(legalScreen('/privacy')?.type).toBe(PrivacyScreen)
    expect(legalScreen('/terms')?.type).toBe(TermsScreen)
    expect(legalScreen('/settings')).toBeUndefined()
  })
})

describe('privacy policy', () => {
  const html = renderToStaticMarkup(<PrivacyScreen />)
  const page = text(html)

  it('covers every kind of data the app keeps and how to delete it', () => {
    for (const heading of ['Your account', 'Your jobs, shifts and vacations', 'Signing in with Google', 'Signing in with LINE',
      'Email codes and emails', 'Cookies and storage on your device', 'Security and abuse protection', 'Google user data',
      'How long data is kept', 'Deleting your data', 'Informational only']) {
      expect(html).toContain(`<h2>${heading}</h2>`)
    }
    expect(page).toContain(`Last updated ${LEGAL_UPDATED}`)
    expect(page).toContain('expire after 10 minutes')
    expect(page).toContain('In Settings, choose Delete account.')
    expect(page).toContain('Some data is not linked to your account and is not deleted with it')
    expect(page).toContain('signs in only with Google or LINE')
    expect(page).toContain('up to 6 hours')
    expect(page).toContain('not legal advice')
  })

  it('names what Google and LINE share and that no access tokens are kept', () => {
    expect(page).toContain('Google account ID, your email address and whether Google has verified it')
    expect(page).toContain('does not keep Google access tokens')
    expect(page).toContain('does not keep LINE access tokens')
    expect(page).toContain('does not currently ask LINE for your email address')
    expect(html).toContain('href="https://developers.google.com/terms/api-services-user-data-policy"')
    expect(page).toContain('Limited Use')
  })

  it('names the services that process data and how to reach the developer', () => {
    for (const service of ['Microsoft Azure', 'Neon', 'Gmail', 'LY Corporation']) {
      expect(page).toContain(service)
    }
    expect(html).toContain(`href="mailto:${BRAND.contactEmail}"`)
    expect(html).toContain('href="/terms"')
  })
})

describe('terms of service', () => {
  const html = renderToStaticMarkup(<TermsScreen />)
  const page = text(html)

  it('says the app is informational only, with the official rules and individual-permission caveat', () => {
    expect(page).toContain('Informational only — not legal advice')
    expect(html).toContain(`href="${BRAND.officialRulesUrl}"`)
    expect(page).toContain('If you hold an individual permission, your limits may differ.')
    expect(page).toContain('Pay figures are estimates.')
    expect(page).toContain('overtime from weekly totals')
  })

  it('links the privacy policy and the contact address', () => {
    expect(html).toContain('href="/privacy"')
    expect(html).toContain(`href="mailto:${BRAND.contactEmail}"`)
    expect(page).toContain(`Last updated ${LEGAL_UPDATED}`)
  })
})

describe('landing page', () => {
  const html = renderToStaticMarkup(<LandingScreen />)
  const page = text(html)

  it('describes the app, how sign-in data is used, and links every next step', () => {
    expect(page).toContain(`${BRAND.name} helps international students in Japan stay within the part-time work limit`)
    expect(page).toContain('28 hours in any 7 consecutive days')
    expect(page).toContain('From Google')
    for (const href of ['/register', '/login', '/privacy', '/terms']) {
      expect(html).toContain(`href="${href}"`)
    }
  })
})

describe('legal links on public screens', () => {
  it('appear on every sign-in screen and in Settings', () => {
    const screens = [
      () => <LoginScreen onSignedIn={signIn} />,
      () => <RegisterScreen onSignedIn={signIn} />,
      () => <ForgotPasswordScreen />,
      () => <WelcomeScreen onSignedIn={signIn} />,
      () => <SettingsScreen user={account} onSignedOut={() => {}} onUpdated={() => {}} />,
    ]
    for (const screen of screens) {
      const html = renderToStaticMarkup(screen())
      expect(html).toContain('href="/privacy"')
      expect(html).toContain('href="/terms"')
    }
  })

  it('ask new accounts to accept the terms before they are created', () => {
    const agreement = 'By creating an account, you agree to the Terms of Service and acknowledge the Privacy Policy.'
    expect(text(renderToStaticMarkup(<RegisterScreen onSignedIn={signIn} />))).toContain(agreement)
    expect(text(renderToStaticMarkup(<WelcomeScreen onSignedIn={signIn} />))).toContain(agreement)
    expect(text(renderToStaticMarkup(<LoginScreen onSignedIn={signIn} />))).not.toContain(agreement)
  })
})
