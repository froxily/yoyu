import { useState, type FormEvent } from 'react'
import { api, ApiError, type ProfileInput, type User, type Verification } from '../api'
import { EmailChange, ReauthenticatePanel } from '../accountAuth'
import { rememberAction } from '../accountActions'
import { useSubmission } from '../authHooks'
import { checkedProfile, loginVerification, needsReauthentication } from '../auth'
import { authMessages } from '../authMessages'
import { AccountAgreement, AuthErrors, AuthField, AuthScreen, CodeStep, PasswordField, ProfileFields, ProfileForm, ProviderButtons, ProviderStatus } from '../authUi'
import { BRAND } from '../brand'
import { useApi } from '../hooks'
import { navigate } from '../router'
import { Link, LoadFailed, Loading, Notice } from '../ui'

interface Props {
  onSignedIn: (user: User) => void | Promise<void>
}

function queryString(): URLSearchParams {
  return new URLSearchParams(typeof window === 'undefined' ? '' : window.location.search)
}

export function LandingScreen() {
  return <AuthScreen title="Stay within your work-hour limit">
    <div className="card auth-card">
      <p className="lead">
        {BRAND.name} helps international students in Japan stay within the part-time work limit for the Student status: 28 hours
        in any 7 consecutive days across all your jobs, or 8 hours a day and 40 hours in any 7 days during your school's long
        vacations.
      </p>
      <div className="stack">
        <Link to="/register" className="button button-primary button-block">Create account</Link>
        <Link to="/login" className="button button-block">Sign in</Link>
      </div>
      <p className="muted small center">Free to use. No ads.</p>
    </div>
    <section className="card">
      <h2>What it does</h2>
      <ul className="bullets">
        <li>Log shifts from every job in one place, including overnight shifts and breaks.</li>
        <li>See how many hours you have left today and on each of the next 7 days.</li>
        <li>Get a warning before you save a future shift that would break the limit. Past shifts are always saved, with any problem shown.</li>
        <li>Add your school's long vacations so the vacation limits apply.</li>
        <li>Estimate your pay, including late-night and overtime premiums.</li>
        <li>Install it on your phone like an app. All times are Japan time.</li>
      </ul>
    </section>
    <section className="card">
      <h2>Your data</h2>
      <ul className="bullets">
        <li>
          Sign in with Google, LINE, or an email address and password. From Google, {BRAND.name} uses your Google account ID, email
          address and name; from LINE, your LINE user ID and display name. They are used only for your account: to create it, sign
          you in, confirm it's you and send account emails.
        </li>
        <li>Your jobs, shifts and vacations are used only to calculate your hours and pay. No ads or tracking, and your data is never sold.</li>
        <li>You can delete your account, with your jobs, shifts and vacations, in Settings at any time.</li>
      </ul>
      <Link to="/privacy" className="auth-text-link">Read the Privacy Policy</Link>
    </section>
  </AuthScreen>
}

export function LoginScreen({ onSignedIn }: Props) {
  const providers = useApi(api.providers)
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [verification, setVerification] = useState<Verification>()
  const task = useSubmission()

  function submit(event: FormEvent) {
    event.preventDefault()
    void task.run(async () => {
      try { await onSignedIn(await api.login(identifier.trim(), password)) } catch (error) {
        const next = loginVerification(error)
        if (next) { setVerification(next); setPassword('') } else if (error instanceof ApiError && error.status === 401) {
          throw new ApiError(401, { detail: 'Incorrect email, username or password.' })
        } else { throw error }
      }
    })
  }

  return <AuthScreen title="Sign in">
    {authMessages('login', queryString()).map((message) => <Notice key={message.text} tone={message.tone}>{message.text}</Notice>)}
    {verification ? <CodeStep verification={verification} email={identifier.includes('@') ? identifier : undefined} onRestart={() => setVerification(undefined)}
      onVerify={async (id, code) => onSignedIn(await api.verifyLogin(id, code))} /> : <div className="card auth-card">
      <p className="lead">A little more room for your day. Keep your part-time jobs in one place.</p>
      <ProviderStatus state={providers} />
      {providers.data && <ProviderButtons providers={providers.data} />}
      {(providers.data?.google || providers.data?.line) && <div className="auth-divider"><span>or</span></div>}
      <form className="form stack" onSubmit={submit} aria-busy={task.busy}>
        <AuthField label="Email or username" name="identifier" autoComplete="username" autoCapitalize="none" spellCheck={false} required
          value={identifier} onChange={(event) => setIdentifier(event.target.value)} error={task.error} disabled={task.busy} />
        <PasswordField value={password} onChange={setPassword} error={task.error} disabled={task.busy} />
        {providers.data?.email && <Link to="/forgot" className="auth-text-link auth-forgot">Forgot password?</Link>}
        <AuthErrors error={task.error} />
        <button type="submit" className="button button-primary button-block" disabled={task.busy}>{task.busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>}
    <p className="auth-footer">New to {BRAND.name}? <Link to="/register" className="auth-text-link">Create an account</Link></p>
  </AuthScreen>
}

export function RegisterScreen({ onSignedIn }: Props) {
  const providers = useApi(api.providers)
  const [profile, setProfile] = useState<ProfileInput>({ fullName: '', username: '' })
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [verification, setVerification] = useState<Verification>()
  const task = useSubmission()
  return <AuthScreen title="Create account">
    {verification ? <CodeStep verification={verification} email={email} restartTo="/register" restartLabel="Use a different email"
      onRestart={() => setVerification(undefined)} onVerify={async (id, code) => onSignedIn(await api.verifyRegistration(id, code))} /> :
      <div className="card auth-card">
        <p className="lead">Make space for work, study and everything in between.</p>
        <ProviderStatus state={providers} />
        {providers.data && <ProviderButtons providers={providers.data} />}
        {providers.data?.email ? <>
          {(providers.data.google || providers.data.line) && <div className="auth-divider"><span>or sign up with email</span></div>}
          <form className="form stack" aria-busy={task.busy} onSubmit={(event) => { event.preventDefault(); void task.run(async () => {
            setVerification(await api.register({ ...checkedProfile(profile), email: email.trim(), password })); setPassword('')
          }) }}>
            <ProfileFields value={profile} onChange={setProfile} error={task.error} disabled={task.busy} />
            <AuthField label="Email" name="email" type="email" autoComplete="email" required value={email}
              onChange={(event) => setEmail(event.target.value)} error={task.error} disabled={task.busy} />
            <PasswordField newPassword label="Password" value={password} onChange={setPassword} error={task.error} disabled={task.busy} />
            <AuthErrors error={task.error} />
            <button type="submit" className="button button-primary button-block" disabled={task.busy}>{task.busy ? 'Sending code…' : 'Create account'}</button>
          </form>
        </> : providers.data && <Notice tone="info">{providers.data.google || providers.data.line
          ? "Email sign-up isn't available yet — continue with Google or LINE."
          : "New accounts aren't available right now. Please try again later."}</Notice>}
        {(providers.data?.email || providers.data?.google || providers.data?.line) && <AccountAgreement />}
      </div>}
    <p className="auth-footer">Already have an account? <Link to="/login" className="auth-text-link">Sign in</Link></p>
  </AuthScreen>
}

export function ForgotPasswordScreen() {
  const providers = useApi(api.providers)
  const [identifier, setIdentifier] = useState('')
  const [verification, setVerification] = useState<Verification>()
  const task = useSubmission()
  return <AuthScreen title="Reset password">
    <ProviderStatus state={providers} />
    {providers.data?.email && (verification ? <CodeStep verification={verification} newPassword submitLabel="Change password" restartTo="/forgot"
      explanation={<p>If an account matches, we've emailed a code.</p>} onRestart={() => setVerification(undefined)}
      onVerify={async (id, code, password) => { await api.resetPassword(id, code, password); navigate('/login?reset=1', { replace: true }) }} /> :
      <form className="card form auth-card" onSubmit={(event) => { event.preventDefault(); void task.run(async () => setVerification(await api.forgotPassword(identifier.trim()))) }}>
        <p>Enter your email or username. We'll help you get back to your account.</p>
        <AuthField label="Email or username" name="identifier" autoComplete="username" autoCapitalize="none" spellCheck={false} required value={identifier}
          onChange={(event) => setIdentifier(event.target.value)} error={task.error} disabled={task.busy} />
        <AuthErrors error={task.error} />
        <button className="button button-primary" type="submit" disabled={task.busy}>{task.busy ? 'Sending code…' : 'Send reset code'}</button>
      </form>)}
    {providers.data && !providers.data.email && <Notice tone="info">Password recovery by email isn't available right now. Sign in with a linked provider or try again later.</Notice>}
    <Link to="/login" className="auth-text-link center">Back to sign in</Link>
  </AuthScreen>
}

export function WelcomeScreen({ user, onSignedIn }: Props & { user?: User }) {
  return user ? <WelcomeProfile user={user} onSignedIn={onSignedIn} /> : <SocialWelcome onSignedIn={onSignedIn} />
}

function WelcomeProfile({ user, onSignedIn }: Props & { user: User }) {
  return <AuthScreen title="Complete your profile">
    <p className="lead">Add your name and choose a username to finish setting up your account.</p>
    <ProfileForm initial={{ fullName: user.fullName ?? '', username: user.username ?? '' }} onSave={async (input) => onSignedIn(await api.updateProfile(input))} />
  </AuthScreen>
}

function SocialWelcome({ onSignedIn }: Props) {
  const pending = useApi(api.socialPending)
  const providers = useApi(api.providers)
  const [completed, setCompleted] = useState<User>()
  const [addingEmail, setAddingEmail] = useState(false)
  const [verification, setVerification] = useState<Verification>()
  const [confirmEmail, setConfirmEmail] = useState<string>()
  const [attemptedEmail, setAttemptedEmail] = useState<string>()
  const [emailBusy, setEmailBusy] = useState(false)
  const [profileBusy, setProfileBusy] = useState(false)
  const task = useSubmission()
  async function requestEmail(email: string) {
    setAttemptedEmail(email)
    try { setVerification(await api.changeEmail(email)) } catch (error) {
      if (needsReauthentication(error)) setConfirmEmail(email)
      else throw error
    }
  }

  return <AuthScreen title={completed ? 'One more thing' : 'Welcome'}>
    {completed ? <>
      <p>Your account is ready. You can also add a verified email for account recovery.</p>
      <p className="muted small">We use your email only to sign you in and recover your account.</p>
      <ProviderStatus state={providers} />
      {providers.data?.email && (confirmEmail ? <ReauthenticatePanel user={completed} providers={providers.data} onCancel={() => setConfirmEmail(undefined)} onBusyChange={setEmailBusy}
        onOAuth={() => rememberAction({ kind: 'email', email: confirmEmail }, completed.id)} onConfirmed={async () => {
          const email = confirmEmail
          setConfirmEmail(undefined)
          await task.run(() => requestEmail(email))
        }} /> :
        addingEmail ? <EmailChange initialEmail={attemptedEmail ?? pending.data?.suggestedEmail ?? ''} verification={verification} error={task.error} disabled={task.busy}
          onRequest={(email) => task.run(() => requestEmail(email))} onVerified={onSignedIn} onBusyChange={setEmailBusy}
          onCancel={() => setAddingEmail(false)} onRestart={() => setVerification(undefined)} /> :
          <button type="button" className="button button-primary auth-wrap" onClick={() => setAddingEmail(true)}>Use this email: {pending.data?.suggestedEmail}</button>)}
      <AuthErrors error={task.error} />
      <button type="button" className="button" disabled={emailBusy || task.busy} onClick={() => void task.run(async () => { await onSignedIn(completed) })}>Skip for now</button>
    </> : pending.error ? pending.error instanceof ApiError && pending.error.status === 404 ?
      <div className="card"><h2>This sign-up expired</h2><p>Start again to create your account.</p><Link to="/login" className="button button-primary">Back to sign in</Link></div> :
      <LoadFailed error={pending.error} onRetry={pending.reload} /> : !pending.data ? <Loading /> : <>
        <p className="lead">Choose the name and username you'll use here.</p>
        {pending.data.email && <Notice tone="info">Your verified email: {pending.data.email}</Notice>}
        <ProfileForm initial={{ fullName: pending.data.suggestedName ?? '', username: '' }} onBusyChange={setProfileBusy} onSave={async (input) => {
          const account = await api.completeSocial(input)
          if (pending.data?.suggestedEmail && account.email === null) setCompleted(account)
          else await onSignedIn(account)
        }} />
        <AccountAgreement />
        <AuthErrors error={task.error} />
        <Link to="/login" className="auth-text-link" aria-disabled={profileBusy || task.busy} onClick={(event) => {
          event.preventDefault()
          if (!profileBusy) void task.run(async () => { await api.logout(); navigate('/login', { replace: true }) })
        }}>Start again</Link>
      </>}
  </AuthScreen>
}
