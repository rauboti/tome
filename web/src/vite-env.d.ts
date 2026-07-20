/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base origin of the tome api for the dev proxy target (see vite.config.ts). */
  readonly VITE_API_ORIGIN?: string
  /** `"true"` starts the MSW worker in dev so the SPA runs without a real api (see main.tsx). */
  readonly VITE_ENABLE_MSW?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
