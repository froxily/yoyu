import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, isStale, messageOf, OFFLINE, UNREACHABLE } from './api'
import { IdempotencyKeys } from './idempotency'

function respond(status: number, body: string, contentType = 'application/problem+json') {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body, { status, headers: { 'Content-Type': contentType } })))
}

async function failure(): Promise<string> {
  try {
    await api.me()
  } catch (error) {
    return messageOf(error)
  }
  throw new Error('expected the request to fail')
}

describe('request errors', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('says the server cannot be reached when the network request fails while online', async () => {
    vi.stubGlobal('navigator', { onLine: true })
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))

    expect(await failure()).toBe(UNREACHABLE)
  })

  it('says the device is offline when the network request fails while offline', async () => {
    vi.stubGlobal('navigator', { onLine: false })
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))

    expect(await failure()).toBe(OFFLINE)
  })

  it.each([502, 503, 504])('says the server cannot be reached for a %i gateway error', async (status) => {
    respond(status, '<html><body>Service Unavailable</body></html>', 'text/html')

    expect(await failure()).toBe(UNREACHABLE)
  })

  it('shows the problem detail of an API error', async () => {
    respond(400, JSON.stringify({ status: 400, detail: 'a shift must end after it starts' }))

    expect(await failure()).toBe('A shift must end after it starts')
  })

  it('does not show a raw status code for an unexpected error without details', async () => {
    respond(500, '<html>oops</html>', 'text/html')

    expect(await failure()).toBe('Something went wrong.')
  })
})

describe('idempotent creates', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('retries a failed create with the same key and uses a new key after it succeeds', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=token' })
    vi.stubGlobal('navigator', { onLine: true })
    const sent: string[] = []
    const fetch = vi.fn(async (_url: string, init: RequestInit) => {
      sent.push((init.headers as Record<string, string>)['Idempotency-Key'])
      if (sent.length === 1) {
        throw new TypeError('Failed to fetch')
      }
      return new Response(JSON.stringify({ id: 'job' }), { status: 201 })
    })
    vi.stubGlobal('fetch', fetch)
    const keys = new IdempotencyKeys()
    const job = { name: 'Cafe', hourlyWageYen: 1200, transportYen: 0, color: '#3b82f6' }

    await expect(api.createJob(job, keys)).rejects.toThrow(UNREACHABLE)
    await api.createJob(job, keys)
    await api.createJob(job, keys)

    expect(sent[0]).toBeTruthy()
    expect(sent[1]).toBe(sent[0])
    expect(sent[2]).not.toBe(sent[1])
  })
})

describe('edits', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('send the version they were loaded with', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=token' })
    const fetch = vi.fn(async (_url: string, _init: RequestInit) => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    await api.updateJob('job', { name: 'Cafe', hourlyWageYen: 1200, transportYen: 0, color: '#3b82f6' }, 3)

    expect(JSON.parse(fetch.mock.calls[0][1].body as string)).toMatchObject({ name: 'Cafe', version: 3 })
  })

  it('recognise a stale edit', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=token' })
    respond(409, JSON.stringify({ status: 409, detail: 'This job was changed elsewhere. Reload and try again.', stale: true }))

    const error = await api
      .updateJob('job', { name: 'Cafe', hourlyWageYen: 1200, transportYen: 0, color: '#3b82f6' }, 0)
      .catch((failure: unknown) => failure)

    expect(isStale(error)).toBe(true)
    expect(isStale(new ApiError(409, { detail: 'This shift overlaps another shift.' }))).toBe(false)
  })
})
