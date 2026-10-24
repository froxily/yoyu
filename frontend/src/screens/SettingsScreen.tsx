import { type FormEvent, useEffect, useEffectEvent, useRef, useState } from 'react'
import { api, type User, type Verification } from '../api'
import { EmailChange, ReauthenticatePanel } from '../accountAuth'
import { clearRememberedAction, readRememberedAction, rememberAction, type AccountAction } from '../accountActions'
import { needsReauthentication, NO_PROVIDERS, oauthStart, passwordSetup } from '../auth'
import { authMessages, PROVIDER_NAMES } from '../authMessages'
import { AuthErrors, PasswordField, ProfileForm, ProviderStatus } from '../authUi'
import { useSubmission } from '../authHooks'
import { useApi } from '../hooks'
import { Disclaimer, Link, Notice, Screen } from '../ui'
import { BRAND } from '../brand'
const SOURCES: { label: string; href: string }[] = [
  {
    label: '28 hours a week, 8 hours a day in long vacations — Immigration Services Agency (official, Japanese)',
    href: BRAND.officialRulesUrl,
  },
  { label: '28 hours in any 7 days, all jobs combined — Shiodome Partners', href: 'https://shiodome.co.jp/column/7855/' },
  { label: 'Long-vacation exception — Mynavi Global', href: 'https://global-saponet.mgl.mynavi.jp/visa/8701' },
  {
    label: '40 hours in any 7 days in long vacations (Labour Standards Act 40-hour week) — Townwork',
    href: 'https://townwork.net/magazine/?p=73524',
  },
  {
    label: 'Checking any 7 consecutive days — freee support',
    href: 'https://support.freee.co.jp/hc/ja/articles/59085137401881',
  },
  { label: 'Weeks that cross into a vacation — jinjibu Q&A', href: 'https://jinjibu.jp/qa/detl/70284/1/' },
  { label: 'Late-night and overtime premiums — Money Forward', href: 'https://biz.moneyforward.com/payroll/basic/82774/' },
  { label: 'Pay example — Townwork', href: 'https://townwork.net/magazine/?p=8000' },
]

export default function SettingsScreen({ user, onSignedOut, onUpdated }: {
  user: User; onSignedOut: () => void; onUpdated?: (user: User) => void
}) {
  const providers = useApi(api.providers)
  const [query] = useState(() => new URLSearchParams(typeof window === 'undefined' ? '' : window.location.search))
  const [resume] = useState(() => readRememberedAction(user.id))
  const confirmed = ['google', 'line'].includes(query.get('reauthenticated') ?? '') && !query.has('error')
  const [account, setAccount] = useState(user)
  const [confirmingDelete, setConfirmingDelete] = useState(resume?.kind === 'delete')
  const [editingEmail, setEditingEmail] = useState(resume?.kind === 'email')
  const [email, setEmail] = useState(resume?.kind === 'email' ? resume.email : '')
  const [editingPassword, setEditingPassword] = useState(resume?.kind === 'password')
  const [newPassword, setNewPassword] = useState('')
  const [verification, setVerification] = useState<Verification>()
  const [pending, setPending] = useState<AccountAction | undefined>(confirmed ? undefined : resume)
  const [notice, setNotice] = useState('')
  const [formBusy, setFormBusy] = useState(false)
  const task = useSubmission()
  const resumed = useRef(false)
  const password = passwordSetup(account)

  function updated(next: User) {
    setAccount(next)
    onUpdated?.(next)
  }

  async function perform(action: AccountAction) {
    setNotice('')
    switch (action.kind) {
      case 'email': {
        const next = await api.changeEmail(action.email)
        setEmail(action.email)
        setEditingEmail(true)
        setVerification(next)
        break
      }
      case 'link': {
        const { redirect } = await api.linkIdentity(action.provider)
        window.location.assign(oauthStart(redirect))
        break
      }
      case 'password': {
        if (!newPassword) {
          setEditingPassword(true)
          break
        }
        const next = await api.setPassword(newPassword)
        setNotice(account.hasPassword ? 'Password changed. Your other devices were signed out.'
          : 'Password set. You can now sign in with your username or email and this password.')
        updated(next)
        setEditingPassword(false)
        setNewPassword('')
        break
      }
      case 'unlink':
        await api.unlinkIdentity(action.provider)
        updated({ ...account, identities: account.identities.filter((identity) => identity.provider !== action.provider) })
        setNotice(`${PROVIDER_NAMES[action.provider]} has been unlinked.`)
        break
      case 'delete':
        await api.deleteAccount()
        onSignedOut()
        break
    }
  }

  async function sensitive(action: AccountAction) {
    try { await perform(action) } catch (error) {
      if (needsReauthentication(error)) {
        if (action.kind === 'email') setEmail(action.email)
        setPending(action)
      }
      else throw error
    }
  }

  const resumeAction = useEffectEvent((action: AccountAction) => { void task.run(() => sensitive(action)) })

  useEffect(() => {
    if (resumed.current) return
    resumed.current = true
    clearRememberedAction()
    if (resume && confirmed && resume.kind !== 'delete' && resume.kind !== 'password') resumeAction(resume)
  }, [resume, confirmed])

  return <div className="account-layout"><Screen title="Settings">
    <nav className="card menu" aria-label="Setup">
      <Link to="/jobs">Jobs <span aria-hidden="true">›</span></Link>
      <Link to="/vacations">Long vacations <span aria-hidden="true">›</span></Link>
    </nav>
    {authMessages('settings', query).map((message) => <Notice key={message.text} tone={message.tone}>{message.text}</Notice>)}
    {notice && <Notice tone="info">{notice}</Notice>}
    <AuthErrors error={task.error} />
    <ProviderStatus state={providers} />
    {pending && <ReauthenticatePanel user={account} providers={providers.data ?? NO_PROVIDERS}
      onCancel={() => setPending(undefined)} onOAuth={() => rememberAction(pending, account.id)}
      onConfirmed={async () => {
        const action = pending
        setPending(undefined)
        await task.run(() => sensitive(action))
      }} />}
    <fieldset className="account-controls" disabled={!!pending || task.busy || formBusy}>
      <ProfileForm title="Profile" submitLabel="Save" initial={{ fullName: account.fullName ?? '', username: account.username ?? '' }}
        onBusyChange={setFormBusy} onSave={async (input) => { updated(await api.updateProfile(input)); setNotice('Profile saved.') }} />
      {providers.data?.email && <section className="card">
        <h2>Email</h2>
        <div className="account-email"><p>{account.email ?? 'No email yet'}</p>
          {account.email && <span className={`badge ${account.emailVerified ? 'badge-ok' : ''}`}>{account.emailVerified ? 'Verified' : 'Not verified'}</span>}
        </div>
        {editingEmail ? <EmailChange key={email} initialEmail={email} verification={verification} error={task.error} onBusyChange={setFormBusy}
          onRequest={(address) => task.run(() => sensitive({ kind: 'email', email: address }))}
          onVerified={(next) => { updated(next); setEditingEmail(false); setVerification(undefined); setNotice('Email verified and updated.') }}
          onCancel={() => { setEditingEmail(false); setVerification(undefined) }} onRestart={() => setVerification(undefined)} /> :
          <button type="button" className="button" onClick={() => { setEmail(''); setEditingEmail(true) }}>{account.email ? 'Change email' : 'Add email'}</button>}
      </section>}
      <section className="card">
        <h2>Sign-in methods</h2>
        <div className="account-method"><div><strong>Password</strong><p className="muted small">{account.hasPassword ? 'Set' : 'Not set'}</p></div>
          {!editingPassword && <button type="button" className="button" disabled={password.needsEmail}
            onClick={() => setEditingPassword(true)}>{password.label}</button>}
        </div>
        {password.needsEmail && <p className="muted small">A password needs a confirmed email address so you can reset it. Add and confirm one first.</p>}
        {editingPassword && <form className="form stack" onSubmit={(event: FormEvent) => { event.preventDefault(); void task.run(() => sensitive({ kind: 'password' })) }}>
          <PasswordField newPassword value={newPassword} onChange={setNewPassword} error={task.error} disabled={task.busy} />
          <div className="actions">
            <button type="submit" className="button button-primary" disabled={task.busy}>{password.label}</button>
            <button type="button" className="button" onClick={() => { setEditingPassword(false); setNewPassword('') }}>Cancel</button>
          </div>
        </form>}
        {(['google', 'line'] as const).filter((provider) => providers.data?.[provider]).map((provider) => {
          const identity = account.identities.find((entry) => entry.provider === provider)
          return <div className="account-method" key={provider}>
            <div><strong>{PROVIDER_NAMES[provider]}</strong><p className="muted small">{identity
              ? <>Linked <time dateTime={identity.linkedAt}>{new Date(identity.linkedAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}</time></>
              : 'Not linked'}</p></div>
            <button type="button" className="button" aria-label={`${identity ? 'Unlink' : 'Link'} ${PROVIDER_NAMES[provider]}`}
              onClick={() => void task.run(() => sensitive({ kind: identity ? 'unlink' : 'link', provider }))}>{identity ? 'Unlink' : 'Link'}</button>
          </div>
        })}
      </section>
      <section className="card">
        <h2>Account</h2>
        <p className="muted">Signed in as {account.fullName ?? account.username ?? account.email}</p>
        <button type="button" className="button button-block" onClick={() => void task.run(async () => { await api.logout(); onSignedOut() })}>Sign out</button>
      </section>
      <section className="card">
        <h2>Delete account</h2>
        {confirmingDelete ? <>
          <Notice tone="violation">This permanently deletes your account and all your jobs, shifts and vacations. It cannot be undone.</Notice>
          <div className="actions">
            <button type="button" className="button button-danger" onClick={() => void task.run(() => sensitive({ kind: 'delete' }))}>Delete everything</button>
            <button type="button" className="button" onClick={() => setConfirmingDelete(false)}>Cancel</button>
          </div>
        </> : <button type="button" className="button button-quiet-danger" onClick={() => setConfirmingDelete(true)}>Delete account</button>}
      </section>
    </fieldset>
    <Disclaimer />
    <section className="card">
      <h2>Sources</h2>
      <ul className="bullets">{SOURCES.map((source) => <li key={source.href}><a href={source.href} target="_blank" rel="noreferrer">{source.label}</a></li>)}</ul>
    </section>
    <nav className="card menu" aria-label="Legal">
      <Link to="/privacy">Privacy Policy <span aria-hidden="true">›</span></Link>
      <Link to="/terms">Terms of Service <span aria-hidden="true">›</span></Link>
    </nav>
  </Screen></div>
}
