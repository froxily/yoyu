import { describe, expect, it } from 'vitest'
import { IdempotencyKeys } from './idempotency'

describe('IdempotencyKeys', () => {
  it('reuses the key when the same body is sent again', () => {
    const keys = new IdempotencyKeys()

    expect(keys.keyFor('{"a":1}')).toBe(keys.keyFor('{"a":1}'))
  })

  it('makes a new key when the body changes', () => {
    const keys = new IdempotencyKeys()

    expect(keys.keyFor('{"a":1}')).not.toBe(keys.keyFor('{"a":2}'))
  })

  it('makes a new key after a request succeeded', () => {
    const keys = new IdempotencyKeys()
    const first = keys.keyFor('{"a":1}')

    keys.reset()

    expect(keys.keyFor('{"a":1}')).not.toBe(first)
  })

  it('makes keys that the server accepts', () => {
    expect(new IdempotencyKeys().keyFor('{}')).toMatch(/^[\x21-\x7E]{1,255}$/)
  })
})
