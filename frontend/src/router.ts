import { useSyncExternalStore } from 'react'

const listeners = new Set<() => void>()

function notify() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  window.addEventListener('popstate', listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('popstate', listener)
  }
}

function currentHref() {
  return window.location.pathname + window.location.search
}

function depth(): number {
  return (window.history.state as { depth?: number } | null)?.depth ?? 0
}

export function navigate(to: string, options: { replace?: boolean } = {}) {
  if (options.replace) {
    window.history.replaceState({ depth: depth() }, '', to)
  } else {
    window.history.pushState({ depth: depth() + 1 }, '', to)
  }
  notify()
  window.scrollTo(0, 0)
}

export function goBack(fallback: string) {
  if (depth() > 0) {
    window.history.back()
  } else {
    navigate(fallback, { replace: true })
  }
}

export function useLocation() {
  const href = useSyncExternalStore(subscribe, currentHref)
  const url = new URL(href, window.location.origin)
  return { href, path: url.pathname, query: url.searchParams }
}
