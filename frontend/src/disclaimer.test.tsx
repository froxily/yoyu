import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { BRAND } from './brand'
import { Disclaimer, Screen } from './ui'

const NOTICE = 'Informational only — not legal advice'
const INDIVIDUAL = 'If you hold an individual permission, your limits may differ.'

describe('disclaimer', () => {
  it('ends every screen with the notice, the official rules link and the individual-permission line', () => {
    const html = renderToStaticMarkup(<Screen title="Any">{null}</Screen>)

    expect(html).toContain(NOTICE)
    expect(html).toContain(`href="${BRAND.officialRulesUrl}"`)
    expect(html).toContain('official rules (ISA)')
    expect(html).toContain(INDIVIDUAL)
    expect(html.indexOf(NOTICE)).toBeLessThan(html.indexOf('official rules (ISA)'))
    expect(html.indexOf('official rules (ISA)')).toBeLessThan(html.indexOf(INDIVIDUAL))
  })

  it('shows the same wording in the full disclaimer', () => {
    const html = renderToStaticMarkup(<Disclaimer />)

    expect(html).toContain(NOTICE)
    expect(html).toContain(`href="${BRAND.officialRulesUrl}"`)
    expect(html).toContain(INDIVIDUAL)
    expect(html).not.toContain('planning aid')
  })
})
