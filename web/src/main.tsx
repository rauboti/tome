import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ThemeProvider, GlobalStyles } from '@rauboti/ui'
import { SessionProvider } from '@/auth/SessionContext'
import { routes } from '@/routes'

const router = createBrowserRouter(routes)

// T022 adds the `import '@/i18n'` side-effect init; T024 adds the MSW mock-mode bootstrap. Kept
// minimal here so those tasks layer in without churn.
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
