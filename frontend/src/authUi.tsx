import { useEffect, useId, useRef, useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from 'react'
import { api, ApiError, type ProfileInput, type Providers, type Verification } from './api'
import { checkedProfile, countdownSeconds, describeInvalid, fieldError, formatCountdown, normalizeUsername, sanitizeCode } from './auth'
import { useSubmission } from './authHooks'
import { BRAND } from './brand'
import { useApi } from './hooks'
import { LegalLinks, Link, LoadFailed, Loading, Notice, Screen } from './ui'

export function AuthScreen({ title, children }: { title: string; children: ReactNode }) {
  return <div className="auth-layout"><Screen title={title}>
    <div className="auth-brand">
      <span className="auth-native" lang="ja">{BRAND.native}</span>
      <span className="auth-name">{BRAND.name}</span>
      <p>{BRAND.tagline}</p>
    </div>
    {children}
    <LegalLinks />
  </Screen></div>
}

export function AccountAgreement() {
  return <p className="muted small">
    By creating an account, you agree to the <a href="/terms" target="_blank" rel="noreferrer">Terms of Service</a> and
    acknowledge the <a href="/privacy" target="_blank" rel="noreferrer">Privacy Policy</a>.
  </p>
}

export function ProviderButtons({ providers }: { providers: Providers }) {
  return <div className="auth-providers">
    {providers.google && <a className="button auth-provider auth-google" href="/oauth2/authorization/google">
      <ProviderIcon provider="google" />Continue with Google
    </a>}
    {providers.line && <a className="button auth-provider auth-line" href="/oauth2/authorization/line">
      <ProviderIcon provider="line" />Continue with LINE
    </a>}
  </div>
}

export function ProviderIcon({ provider }: { provider: 'google' | 'line' }) {
  return provider === 'google' ? <svg viewBox="0 0 18 18" aria-hidden="true" focusable="false">
    <path fill="#4285F4" d="M17.64 9.2045c0-.6382-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2582h2.9091c1.7023-1.5673 2.6832-3.8755 2.6832-6.6151Z" />
    <path fill="#34A853" d="M9 18c2.43 0 4.4673-.8059 5.9568-2.1804l-2.9091-2.2582c-.8059.54-1.8368.8591-3.0477.8591-2.3441 0-4.3282-1.5832-5.0359-3.7105H.9568v2.3318A9 9 0 0 0 9 18Z" />
    <path fill="#FBBC05" d="M3.9641 10.71A5.41 5.41 0 0 1 3.6818 9c0-.5932.1023-1.17.2823-1.71V4.9582H.9568A9 9 0 0 0 0 9c0 1.4523.3477 2.8273.9568 4.0418l3.0073-2.3318Z" />
    <path fill="#EA4335" d="M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.3459l2.5814-2.5814C13.4632.8918 11.4259 0 9 0A9 9 0 0 0 .9568 4.9582L3.9641 7.29C4.6718 5.1627 6.6559 3.5795 9 3.5795Z" />
  </svg> : <svg viewBox="0 0 40 40" aria-hidden="true" focusable="false">
    <path fill="white" d="M36 18c0-7.2-7.2-13-16-13S4 10.8 4 18c0 6.5 5.6 11.8 13.1 12.8.5.1 1.2.4 1.4.8.2.4.1 1.1.1 1.5l-.3 1.5c-.1.5-.4 1.9 1.7 1s11.2-6.5 14-10.1A11.7 11.7 0 0 0 36 18Z" />
    <path fill="none" stroke="#06C755" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" d="M10 15v7h4m3-7v7m4 0v-7l4 7v-7m6 0h-3v7h3m-3-3.5h3" />
  </svg>
}

export function ProviderStatus({ state }: { state: ReturnType<typeof useApi<Providers>> }) {
  return state.error ? <LoadFailed error={state.error} onRetry={state.reload} /> : !state.data ? <Loading /> : null
}

export function AuthErrors({ error }: { error?: unknown }) {
  return <div aria-live="polite" aria-atomic="true" className="auth-errors">
    {error !== undefined && <Notice tone="error">{describeInvalid(error)}</Notice>}
  </div>
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  error?: unknown
}

export function AuthField({ label, hint, error, name, ...input }: FieldProps) {
  const id = useId()
  const message = fieldError(error, name ?? '')
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <input {...input} id={id} name={name} aria-invalid={!!message} aria-describedby={[hint && `${id}-hint`, message && `${id}-error`].filter(Boolean).join(' ') || undefined} />
    {hint && <small id={`${id}-hint`}>{hint}</small>}
    {message && <p id={`${id}-error`} className="field-error" role="alert">{message}</p>}
  </div>
}

export function PasswordField({ value, onChange, error, newPassword = false, disabled = false, label }: {
  value: string; onChange: (value: string) => void; error?: unknown; newPassword?: boolean; disabled?: boolean
  label?: string
}) {
  const [visible, setVisible] = useState(false)
  const id = useId()
  const message = fieldError(error, 'password')
  return <div className="field">
    <label htmlFor={id}>{label ?? (newPassword ? 'New password' : 'Password')}</label>
    <div className="auth-password">
      <input id={id} name="password" type={visible ? 'text' : 'password'} required disabled={disabled}
        autoComplete={newPassword ? 'new-password' : 'current-password'} minLength={newPassword ? 10 : undefined}
        value={value} onChange={(event) => onChange(event.target.value)} aria-invalid={!!message}
        aria-describedby={[newPassword && `${id}-hint`, message && `${id}-error`].filter(Boolean).join(' ') || undefined} />
      <button type="button" className="auth-password-toggle" disabled={disabled} aria-label={visible ? 'Hide password' : 'Show password'} aria-pressed={visible}
        onClick={() => setVisible(!visible)}>{visible ? 'Hide' : 'Show'}</button>
    </div>
    {newPassword && <small id={`${id}-hint`}>At least 10 characters. Avoid common passwords.</small>}
    {message && <p id={`${id}-error`} className="field-error" role="alert">{message}</p>}
  </div>
}

export function ProfileFields({ value, onChange, error, disabled }: {
  value: ProfileInput; onChange: (value: ProfileInput) => void; error?: unknown; disabled?: boolean
}) {
  return <>
    <AuthField label="Full name" name="fullName" autoComplete="name" required value={value.fullName} disabled={disabled}
      onChange={(event) => onChange({ ...value, fullName: event.target.value })} error={error} />
    <AuthField label="Username" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false}
      hint="3–20 letters, numbers or underscores" required minLength={3} maxLength={20} pattern="[a-z0-9_]{3,20}"
      value={value.username} disabled={disabled} onChange={(event) => onChange({ ...value, username: normalizeUsername(event.target.value) })} error={error} />
  </>
}

export function ProfileForm({ initial, onSave, title, submitLabel = 'Continue', onBusyChange }: {
  initial: ProfileInput; onSave: (profile: ProfileInput) => Promise<void>; title?: string; submitLabel?: string
  onBusyChange?: (busy: boolean) => void
}) {
  const [profile, setProfile] = useState(initial)
  const task = useSubmission(onBusyChange)
  return <form className="card form auth-card" aria-busy={task.busy} onSubmit={(event) => {
    event.preventDefault()
    void task.run(() => onSave(checkedProfile(profile)))
  }}>
    {title && <h2>{title}</h2>}
    <ProfileFields value={profile} onChange={setProfile} error={task.error} disabled={task.busy} />
    <AuthErrors error={task.error} />
    <button type="submit" className="button button-primary" disabled={task.busy}>{task.busy ? 'Saving…' : submitLabel}</button>
  </form>
}

export function CodeStep({ verification: initial, email, onVerify, onRestart, restartTo = '/login', restartLabel = 'Start again', newPassword = false, explanation, submitLabel = 'Verify email', onBusyChange }: {
  verification: Verification; email?: string | null; onVerify: (verificationId: string, code: string, password: string) => Promise<void>
  onRestart: () => void; restartTo?: string; restartLabel?: string; newPassword?: boolean; explanation?: ReactNode; submitLabel?: string
  onBusyChange?: (busy: boolean) => void
}) {
  const [verification, setVerification] = useState(initial)
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [deadline, setDeadline] = useState(() => Date.now() + countdownSeconds(initial.resendAfterSeconds ?? 0) * 1000)
  const [remaining, setRemaining] = useState(() => countdownSeconds(initial.resendAfterSeconds ?? 0))
  const [sent, setSent] = useState(false)
  const task = useSubmission(onBusyChange)
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  useEffect(() => {
    const update = () => setRemaining(countdownSeconds((deadline - Date.now()) / 1000))
    update()
    const timer = window.setInterval(update, 1000)
    return () => window.clearInterval(timer)
  }, [deadline])

  async function resend() {
    await task.run(async () => {
      try {
        const next = await api.resendCode(verification.verificationId)
        setVerification(next)
        setCode('')
        setSent(true)
        setDeadline(Date.now() + countdownSeconds(next.resendAfterSeconds ?? 0) * 1000)
      } catch (error) {
        if (error instanceof ApiError && error.status === 429) {
          setDeadline(Date.now() + countdownSeconds(error.problem.retryAfterSeconds ?? 60) * 1000)
        }
        throw error
      }
    })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void task.run(async () => {
      if (!/^\d{6}$/.test(code)) throw new ApiError(400, { errors: [{ field: 'code', message: 'Enter the six-digit code.' }] })
      await onVerify(verification.verificationId, code, password)
    })
  }

  return <form className="card form auth-code-step" onSubmit={submit} aria-busy={task.busy}>
    <h2 ref={heading} tabIndex={-1}>Check your email</h2>
    {explanation ?? <p>We emailed a code to <strong>{email || 'your email'}</strong>.</p>}
    <AuthField label="Six-digit code" name="code" type="text" className="auth-code" inputMode="numeric" autoComplete="one-time-code"
      pattern="\d{6}" maxLength={6} required value={code} disabled={task.busy} onChange={(event) => setCode(sanitizeCode(event.target.value))} error={task.error} />
    {newPassword && <PasswordField newPassword value={password} onChange={setPassword} error={task.error} disabled={task.busy} />}
    <AuthErrors error={task.error} />
    <button className="button button-primary button-block" type="submit" disabled={task.busy}>{task.busy ? 'Please wait…' : submitLabel}</button>
    <button className="button button-block" type="button" disabled={task.busy || remaining > 0} onClick={() => void resend()}>
      {remaining > 0 ? `Resend code in ${formatCountdown(remaining)}` : 'Resend code'}
    </button>
    <span className="auth-sr-only" role="status" aria-live="polite">{remaining > 0 ? `You can resend the code in ${formatCountdown(remaining)}.` : 'You can resend the code now.'}</span>
    {sent && <Notice tone="info">A new code has been requested. Check your email.</Notice>}
    <p className="muted small">Didn't get it? Check your spam folder.</p>
    <Link to={restartTo} className="auth-text-link" aria-disabled={task.busy} onClick={(event) => { event.preventDefault(); if (!task.busy) onRestart() }}>{restartLabel}</Link>
  </form>
}
