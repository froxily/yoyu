import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearRememberedAction, readRememberedAction, rememberAction, type AccountAction } from './accountActions'

describe('resuming an account action after OAuth', () => {
  let entries: Map<string, string>
  beforeEach(() => {
    entries = new Map()
    vi.stubGlobal('window', { sessionStorage: {
      setItem: (key: string, value: string) => entries.set(key, value),
      getItem: (key: string) => entries.get(key) ?? null,
      removeItem: (key: string) => entries.delete(key),
    } })
  })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

  it.each([
    { kind: 'delete' }, { kind: 'password' }, { kind: 'email', email: 'a@example.com' },
    { kind: 'link', provider: 'google' }, { kind: 'unlink', provider: 'line' },
  ] satisfies AccountAction[])('retains $kind only for the account that requested it', (action) => {
    rememberAction(action, 'original')
    expect(readRememberedAction('original')).toEqual(action)
    expect(readRememberedAction('different')).toBeUndefined()
    clearRememberedAction()
    expect(readRememberedAction('original')).toBeUndefined()
  })

  it('never stores a new password while confirming it is you', () => {
    rememberAction({ kind: 'password', password: 'secret passphrase' } as unknown as AccountAction, 'account')
    expect([...entries.values()].join()).toContain('"kind":"password"')
    expect([...entries.values()].join()).not.toContain('secret passphrase')
    expect(readRememberedAction('account')).toEqual({ kind: 'password' })
  })

  it('expires the action after fifteen minutes', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1000)
    rememberAction({ kind: 'delete' }, 'account')
    vi.spyOn(Date, 'now').mockReturnValue(1000 + 15 * 60 * 1000 + 1)
    expect(readRememberedAction('account')).toBeUndefined()
  })

  it.each(['invalid JSON', 'null', '{"action":{"kind":"unknown"},"expires":9999999999999,"userId":"account"}'])('ignores invalid stored actions', (raw) => {
    entries.set('account-confirmation', raw)
    expect(readRememberedAction('account')).toBeUndefined()
  })

  it('can render safely when session storage is unavailable', () => {
    vi.stubGlobal('window', undefined)
    expect(readRememberedAction('account')).toBeUndefined()
    expect(() => clearRememberedAction()).not.toThrow()
  })
})
