import { readFileSync } from 'node:fs'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { BRAND } from '../brand'
import { scheduleSplashEnd, SPLASH_FALLBACK_MS } from './splashTimer'
import YoyuSplash from './YoyuSplash'

const css = readFileSync('src/components/YoyuSplash.css', 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')

function cssRules(source: string) {
  const rules: { selector: string, body: string, parents: string[] }[] = []
  const stack: { selector: string, start: number }[] = []
  let start = 0
  for (let i = 0; i < source.length; i++) {
    if (source[i] === '{') {
      stack.push({ selector: source.slice(start, i).trim(), start: i + 1 })
      start = i + 1
    } else if (source[i] === '}') {
      const rule = stack.pop()!
      rules.push({
        selector: rule.selector,
        body: source.slice(rule.start, i),
        parents: stack.map(({ selector }) => selector),
      })
      start = i + 1
    }
  }
  return rules
}

const rules = cssRules(css)

describe('splash', () => {
  afterEach(() => vi.useRealTimers())

  it('renders decorative brand markup without interactive elements', () => {
    const html = renderToStaticMarkup(<YoyuSplash onDone={() => {}} />)
    const root = html.match(/^<[^>]+>/)?.[0]

    expect(root).toContain('aria-hidden="true"')
    expect(root).toContain('class="yoyu-splash"')
    expect(html).toContain(`lang="ja">${BRAND.native}`)
    expect(html).toContain(BRAND.name)
    expect(html).toContain(BRAND.tagline)
    for (const interactive of ['<a', '<button', '<input', 'tabindex']) {
      expect(html).not.toContain(interactive)
    }
  })

  it('ends exactly once at the fallback delay', () => {
    vi.useFakeTimers()
    const onDone = vi.fn()
    scheduleSplashEnd(onDone)

    vi.advanceTimersByTime(SPLASH_FALLBACK_MS - 1)
    expect(onDone).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onDone).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(SPLASH_FALLBACK_MS * 10)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('cancels the fallback when cleaned up before the delay', () => {
    vi.useFakeTimers()
    const onDone = vi.fn()
    const cleanup = scheduleSplashEnd(onDone)

    vi.advanceTimersByTime(SPLASH_FALLBACK_MS - 1)
    cleanup()
    vi.advanceTimersByTime(SPLASH_FALLBACK_MS * 10)
    expect(onDone).not.toHaveBeenCalled()
  })

  it('honours a custom delay', () => {
    vi.useFakeTimers()
    const onDone = vi.fn()
    scheduleSplashEnd(onDone, 150)

    vi.advanceTimersByTime(149)
    expect(onDone).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('scopes selectors and keyframes without external assets or click-through', () => {
    const selectors = rules
      .filter(({ selector, parents }) => !selector.startsWith('@') && !parents.some((parent) => /^@keyframes\b/i.test(parent)))
      .flatMap(({ selector }) => selector.split(',').map((part) => part.trim()))

    expect(selectors.length).toBeGreaterThan(0)
    for (const selector of selectors) {
      expect(selector.startsWith('.yoyu-splash') || selector === '#root:empty', selector).toBe(true)
    }
    for (const [, name] of css.matchAll(/@keyframes\s+([^\s{]+)/gi)) {
      expect(name).toMatch(/^yoyu-splash-/)
    }
    expect(css).not.toMatch(/@import\b/i)
    expect(css).not.toMatch(/url\s*\(/i)
    expect(css).not.toMatch(/\bpointer-events\s*:\s*none\b/i)
  })

  it('uses a short fade for reduced motion and the default exit otherwise', () => {
    const media = rules.find(({ selector }) => /^@media\s*\(\s*prefers-reduced-motion\s*:\s*reduce\s*\)$/.test(selector))
    expect(media).toBeDefined()

    const reduced = cssRules(media!.body).find(({ selector }) => selector === '.yoyu-splash')
    const normal = rules.find(({ selector, parents }) => selector === '.yoyu-splash' && parents.length === 0)
    expect(reduced?.body).toMatch(/\banimation\s*:\s*yoyu-splash-fade\s+150ms\b/)
    expect(normal?.body).toMatch(/\banimation\s*:\s*yoyu-splash-exit\s+1\.2s\b/)
  })
})
