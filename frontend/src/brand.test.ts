import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { BRAND } from './brand'

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      return sourceFiles(path)
    }
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) ? [path] : []
  })
}

describe('brand', () => {
  it('is named Yoyū (余裕) with an ASCII slug', () => {
    expect(BRAND.full).toBe('Yoyū (余裕)')
    expect(BRAND.slug).toBe('yoyu')
  })

  it('is the only place that names the product', () => {
    const files = [...sourceFiles('src').filter((file) => !file.endsWith('brand.ts')), 'index.html', 'vite.config.ts']
    for (const file of files) {
      const text = readFileSync(file, 'utf8')
      expect(text, file).not.toMatch(/Shift28|Yoyū|余裕/)
    }
  })
})
