import { useEffect, type AnimationEvent } from 'react'
import { BRAND } from '../brand'
import { scheduleSplashEnd } from './splashTimer'
import './YoyuSplash.css'

export default function YoyuSplash({ onDone }: { onDone: () => void }) {
  useEffect(() => scheduleSplashEnd(onDone), [onDone])

  function ended(event: AnimationEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) {
      onDone()
    }
  }

  return (
    <div className="yoyu-splash" aria-hidden="true" onAnimationEnd={ended}>
      <div className="yoyu-splash-stage">
        <div className="yoyu-splash-brand">
          <div className="yoyu-splash-ripple" />
          <div className="yoyu-splash-ripple yoyu-splash-ripple-two" />
          <div className="yoyu-splash-kanji" lang="ja">
            {BRAND.native}
          </div>
          <div className="yoyu-splash-name">{BRAND.name}</div>
        </div>
        <div className="yoyu-splash-bottom">
          <span>{BRAND.tagline}</span>
          <div className="yoyu-splash-line" />
        </div>
      </div>
    </div>
  )
}
