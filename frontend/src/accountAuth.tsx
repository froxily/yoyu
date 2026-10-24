import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, type Provider, type Providers, type User, type Verification } from './api'
import { oauthStart, reauthOptions } from './auth'
import { PROVIDER_NAMES } from './authMessages'
import { useSubmission } from './authHooks'
import { AuthErrors, AuthField, CodeStep, PasswordField, ProviderIcon } from './authUi'
import { Notice } from './ui'

export function ReauthenticatePanel({ user, providers, onConfirmed, onCancel, onOAuth, onBusyChange }: {
  user: User; providers: Providers; onConfirmed: () => Promise<void>; onCancel: () => void; onOAuth: () => void
  onBusyChange?: (busy: boolean) => void
}) {
  const options = reauthOptions(user, providers)
  const [password, setPassword] = useState('')
  const [verification, setVerification] = useState<Verification>()
  const [codeBusy, setCodeBusy] = useState(false)
  const task = useSubmission(onBusyChange)
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  const available = Object.values(options).some(Boolean)

  async function social(provider: Provider) {
    await task.run(async () => {
      const { redirect } = await api.reauthenticateIdentity(provider)
      onOAuth()
      window.location.assign(oauthStart(redirect))
    })
  }

  return <section className="card auth-reauth" aria-label="Confirm it's you" aria-busy={task.busy}>
    <h2 ref={heading} tabIndex={-1}>Confirm it's you</h2>
    <p>Confirm your identity to finish this account change.</p>
    {!available && <Notice tone="info">This action can't be confirmed right now. Your sign-in methods are unavailable. Please try again later.</Notice>}
    {verification ? <CodeStep verification={verification} email={user.email} submitLabel="Confirm identity" restartLabel="Choose another method" restartTo="/settings"
      onBusyChange={(busy) => { setCodeBusy(busy); onBusyChange?.(busy) }}
      onRestart={() => setVerification(undefined)} onVerify={async (id, code) => { await api.verifyReauthentication(id, code); await onConfirmed() }} /> : <>
      {options.password && <form className="form stack" onSubmit={(event: FormEvent) => { event.preventDefault(); void task.run(async () => { await api.reauthenticate(password); await onConfirmed() }) }}>
        <PasswordField value={password} onChange={setPassword} error={task.error} disabled={task.busy} />
        <button type="submit" className="button button-primary" disabled={task.busy}>Confirm with password</button>
      </form>}
      {options.email && <button type="button" className="button" disabled={task.busy} onClick={() => void task.run(async () => setVerification(await api.reauthenticateEmail()))}>Email me a code</button>}
      {(['google', 'line'] as const).filter((provider) => options[provider]).map((provider) =>
        <button type="button" key={provider} className={`button auth-provider auth-${provider}`} disabled={task.busy} onClick={() => void social(provider)}>
          <ProviderIcon provider={provider} />Continue with {PROVIDER_NAMES[provider]}
        </button>)}
    </>}
    <AuthErrors error={task.error} />
    <button type="button" className="button" disabled={task.busy || codeBusy} onClick={onCancel}>Cancel confirmation</button>
  </section>
}

export function EmailChange({ initialEmail = '', verification, error, disabled = false, onRequest, onVerified, onCancel, onRestart, onBusyChange }: {
  initialEmail?: string; verification?: Verification; onRequest: (email: string) => Promise<void>
  error?: unknown; disabled?: boolean; onVerified: (user: User) => void | Promise<void>; onCancel: () => void; onRestart: () => void
  onBusyChange?: (busy: boolean) => void
}) {
  const [email, setEmail] = useState(initialEmail)
  const task = useSubmission(onBusyChange)
  const busy = disabled || task.busy
  return verification ? <CodeStep verification={verification} email={email} restartTo="/settings" restartLabel="Use a different email" onBusyChange={onBusyChange}
    explanation={<p>If this address is available, we've emailed a code to <strong>{email}</strong>. Your current email stays the same until you verify.</p>}
    onRestart={onRestart} onVerify={async (id, code) => onVerified(await api.verifyEmail(id, code))} /> :
    <form className="form stack" aria-busy={busy} onSubmit={(event) => { event.preventDefault(); if (!busy) void task.run(() => onRequest(email.trim())) }}>
      <AuthField label="Email address" name="email" type="email" autoComplete="email" required value={email} disabled={busy}
        onChange={(event) => setEmail(event.target.value)} error={task.error ?? error} />
      <p className="muted small">We'll send a code if this address is available. Verify it to add it to your account.</p>
      <AuthErrors error={task.error} />
      <div className="actions">
        <button type="submit" className="button button-primary" disabled={busy}>Send code</button>
        <button type="button" className="button" disabled={busy} onClick={onCancel}>Cancel</button>
      </div>
    </form>
}
