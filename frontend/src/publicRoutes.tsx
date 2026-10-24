import type { User } from './api'
import { ForgotPasswordScreen, LandingScreen, LoginScreen, RegisterScreen, WelcomeScreen } from './screens/AuthScreens'
import { PrivacyScreen, TermsScreen } from './screens/LegalScreens'

export const PUBLIC_PATHS = ['/', '/login', '/register', '/forgot', '/welcome', '/privacy', '/terms']

export function legalScreen(path: string) {
  if (path === '/privacy') return <PrivacyScreen />
  if (path === '/terms') return <TermsScreen />
}

export function signedOutScreen(path: string, href: string, onSignedIn: (user: User) => Promise<void>) {
  switch (path) {
    case '/': return <LandingScreen />
    case '/register': return <RegisterScreen onSignedIn={onSignedIn} />
    case '/forgot': return <ForgotPasswordScreen />
    case '/welcome': return <WelcomeScreen onSignedIn={onSignedIn} />
    default: return legalScreen(path) ?? <LoginScreen key={href} onSignedIn={onSignedIn} />
  }
}
