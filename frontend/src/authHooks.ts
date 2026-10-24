import { useRef, useState } from 'react'

export function useSubmission(onBusyChange?: (busy: boolean) => void) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const running = useRef(false)
  async function run(action: () => Promise<void>) {
    if (running.current) return
    running.current = true
    setBusy(true)
    onBusyChange?.(true)
    setError(undefined)
    try { await action() } catch (failure) { setError(failure) } finally {
      running.current = false
      setBusy(false)
      onBusyChange?.(false)
    }
  }
  return { busy, error, run }
}
