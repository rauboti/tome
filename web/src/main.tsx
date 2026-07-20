import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ThemeProvider, GlobalStyles } from '@rauboti/ui'
import { routes } from '@/routes'

const router = createBrowserRouter(routes)

// Scaffold state (T020): ThemeProvider + Router only. T021 wraps the tree in the session
// AuthProvider; T022 adds the `import '@/i18n'` side-effect init; T024 adds the MSW mock-mode
// bootstrap. Kept minimal here so those tasks layer in without churn.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <GlobalStyles />
      <RouterProvider router={router} />
    </ThemeProvider>
  </StrictMode>,
)
