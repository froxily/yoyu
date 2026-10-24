import type { Provider } from './api'

export type AccountAction = { kind: 'delete' | 'password' } | { kind: 'email'; email: string } | { kind: 'link' | 'unlink'; provider: Provider }
const ACTION_KEY = 'account-confirmation'

export function rememberAction(action: AccountAction, userId: string) {
  window.sessionStorage.setItem(ACTION_KEY, JSON.stringify({ action: known(action), userId, expires: Date.now() + 15 * 60 * 1000 }))
}

export function readRememberedAction(userId: string): AccountAction | undefined {
  try {
    const raw = window.sessionStorage.getItem(ACTION_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    const { action, expires } = saved
    if (saved.userId !== userId || typeof expires !== 'number' || expires < Date.now() || !action) return
    return known(action)
  } catch { return }
}

function known(action: AccountAction): AccountAction | undefined {
  if (action.kind === 'delete' || action.kind === 'password') return { kind: action.kind }
  if (action.kind === 'email' && typeof action.email === 'string') return { kind: 'email', email: action.email }
  if ((action.kind === 'link' || action.kind === 'unlink') && (action.provider === 'google' || action.provider === 'line')) {
    return { kind: action.kind, provider: action.provider }
  }
}

export function clearRememberedAction() {
  try { window.sessionStorage.removeItem(ACTION_KEY) } catch { return }
}
