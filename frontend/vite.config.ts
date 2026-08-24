import path from 'node:path'
import { defineConfig } from 'vite'
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
  build: {
    // Lands in the Spring resource tree so `-Pfrontend package` ships the SPA in the jar.
    outDir: path.resolve(import.meta.dirname, '../src/main/resources/static'),
    emptyOutDir: true, // required: outDir is outside the Vite root
  },
})
