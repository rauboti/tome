import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ThemeProvider, GlobalStyles } from '@rauboti/ui'
import { SessionProvider } from '@/auth/SessionContext'
import { routes } from '@/routes'
import '@/i18n'

const router = createBrowserRouter(routes)

/** In mock mode (`VITE_ENABLE_MSW=true`) start the MSW worker before rendering so the first
 *  `/api/auth/me` probe is intercepted. A normal `yarn dev` skips this and hits the real api via
 *  the Vite proxy. */
const enableMocking = async (): Promise<void> => {
  if (import.meta.env.VITE_ENABLE_MSW !== 'true') return
  const { worker } = await import('@/mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ThemeProvider>
        <GlobalStyles />
        <SessionProvider>
          <RouterProvider router={router} />
        </SessionProvider>
      </ThemeProvider>
    </StrictMode>,
  )
})
