import path from 'node:path'
// `vitest/config` re-exports Vite's own `defineConfig` with the `test` block typed, so the
// test run reuses this file's resolution — the `@/` alias above included — rather than
// maintaining a second one that could drift from it.
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const BACKEND = 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  server: {
    // Vite 8 binds [::1] only by default, so 127.0.0.1:5173 would refuse connections.
    // Pin it to IPv4 loopback to match how the backend is addressed everywhere else.
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    // Same-origin by proxy. This is why the backend needs no CORS configuration
    // and why no VITE_API_BASE exists anywhere in this project.
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: false,
        // The first PDF request renders through Playwright, which downloads
        // Chromium on first use. Do not let the proxy give up on it.
        timeout: 600_000,
        proxyTimeout: 600_000,
      },
      '/actuator': { target: BACKEND, changeOrigin: false },
    },
  },
  test: {
    // Everything under test is a pure function: no DOM, no rendering, no jsdom to configure.
    environment: 'node',
    // The date formatters read the ambient zone, so a run in Warsaw and a run on a UTC CI box
    // would disagree about which day an ISO instant falls on. Pin it rather than assert loosely.
    env: { TZ: 'UTC' },
  },

  build: {
    // Lands in the Spring resource tree so `-Pfrontend package` ships the SPA in the jar.
    outDir: path.resolve(import.meta.dirname, '../src/main/resources/static'),
    emptyOutDir: true, // required: outDir is outside the Vite root
  },
})
