import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

const background = { resizeOptions: { background: '#0f766e' } }

export default defineConfig({
  preset: {
    ...minimal2023Preset,
    maskable: { ...minimal2023Preset.maskable, ...background },
    apple: { ...minimal2023Preset.apple, ...background },
  },
  images: ['public/favicon.svg'],
})
