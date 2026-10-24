export const SPLASH_FALLBACK_MS = 2500

export function scheduleSplashEnd(onDone: () => void, delay = SPLASH_FALLBACK_MS) {
  const timer = setTimeout(onDone, delay)
  return () => clearTimeout(timer)
}
