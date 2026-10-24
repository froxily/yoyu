import { useEffect, useRef, useState } from 'react'
import { api, ApiError, messageOf, type CheckResult, type ShiftDraft } from './api'
import { msUntilNextJstMidnight, todayJst } from './time'

interface Loaded<T> {
  data?: T
  error?: unknown
  reload: () => void
}

export function useApi<T>(load: () => Promise<T>): Loaded<T> {
  const [initialLoad] = useState(() => load)
  const [version, setVersion] = useState(0)
  const [state, setState] = useState<{ version: number; data?: T; error?: unknown }>({ version: -1 })

  useEffect(() => {
    let active = true
    initialLoad().then(
      (data) => active && setState({ version, data }),
      (error: unknown) => active && setState((previous) => ({ version, data: previous.data, error })),
    )
    return () => {
      active = false
    }
  }, [initialLoad, version])

  return {
    data: state.data,
    error: state.version === version ? state.error : undefined,
    reload: () => setVersion((value) => value + 1),
  }
}

export interface LiveCheck {
  pending: boolean
  result?: CheckResult
  error?: string
}

export function useLiveCheck(draft: ShiftDraft | null, excludeShiftId: string | undefined): LiveCheck {
  const key = draft === null ? '' : JSON.stringify(draft)
  const [state, setState] = useState<{ key: string; result?: CheckResult; error?: string }>({ key: '' })

  useEffect(() => {
    if (key === '') {
      return
    }
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      api.check(JSON.parse(key) as ShiftDraft, excludeShiftId, controller.signal).then(
        (result) => setState({ key, result }),
        (error: unknown) => {
          if (!controller.signal.aborted && !(error instanceof ApiError && error.status === 401)) {
            setState({ key, error: messageOf(error) })
          }
        },
      )
    }, 400)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [key, excludeShiftId])

  if (key === '') {
    return { pending: false }
  }
  return { pending: state.key !== key, result: state.result, error: state.error }
}

export function useJstToday(): string {
  const [today, setToday] = useState(() => todayJst())

  useEffect(() => {
    const check = () => setToday(todayJst())
    const timer = window.setTimeout(check, msUntilNextJstMidnight(Date.now()) + 1000)
    document.addEventListener('visibilitychange', check)
    return () => {
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', check)
    }
  }, [today])

  return today
}

export function useRefreshOnVisible(refresh: () => void) {
  const latest = useRef(refresh)

  useEffect(() => {
    latest.current = refresh
  })

  useEffect(() => {
    const onChange = () => {
      if (document.visibilityState === 'visible') {
        latest.current()
      }
    }
    document.addEventListener('visibilitychange', onChange)
    return () => document.removeEventListener('visibilitychange', onChange)
  }, [])
}

export function useOnline(): boolean {
  const [online, setOnline] = useState(() => navigator.onLine)

  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])

  return online
}
