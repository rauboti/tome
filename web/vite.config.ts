/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Resolve the `@/*` alias from tsconfig `paths` (native to Vite 8+).
  resolve: { tsconfigPaths: true },
  server: {
    // Vite's default dev port (5173); honour PORT when tooling assigns one.
    port: process.env.PORT ? Number(process.env.PORT) : 5173,
    // Dev proxy: relative `/api` (data) and `/auth` (BFF OAuth login/callback) requests
    // are forwarded to the Tome api on :5040 (its published Docker port — run it via
    // `docker compose up tome-db tome-api`). Override with VITE_API_ORIGIN if you run
    // the api elsewhere. Once MSW is added (T024), the worker intercepts `/api` before the
    // network, making it inert for data; `/auth` is a full-page navigation, so it always
    // hits this proxy.
    proxy: {
      '/api': {
        target: process.env.VITE_API_ORIGIN ?? 'http://localhost:5040',
        changeOrigin: true,
      },
      '/auth': {
        target: process.env.VITE_API_ORIGIN ?? 'http://localhost:5040',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
