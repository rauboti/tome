import { describe, expect, test } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { http, HttpResponse, delay } from 'msw'
import { ThemeProvider } from '@rauboti/ui'
import { SessionProvider, useSession } from './SessionContext'
import { RequireAuth } from './RequireAuth'
import { server } from '@/mocks/server'
import { authenticatedUser } from '@/mocks/fixtures'

/** A protected child that also exposes sign-out, so the guard's states and logout are testable
 *  without the full navbar/UserMenu. */
const Protected = () => {
  const { signOut } = useSession()
  return (
    <div>
      <span>protected content</span>
      <button onClick={() => void signOut()}>logout</button>
    </div>
  )
}

const renderApp = () => {
  const router = createMemoryRouter(
    [
      {
        element: <RequireAuth />,
        children: [{ path: '/', element: <Protected /> }],
      },
    ],
    { initialEntries: ['/'] },
  )
  render(
    <ThemeProvider>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </ThemeProvider>,
  )
}

describe('session + RequireAuth', () => {
  test('shows a loading status while the probe is in flight', () => {
    server.use(
      http.get('/api/auth/me', async () => {
        await delay('infinite')
        return HttpResponse.json(authenticatedUser)
      }),
    )
    renderApp()
    expect(
      screen.getByRole('status', { name: /checking your session/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the login screen when unauthenticated (401)', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
    )
    renderApp()
    expect(
      await screen.findByRole('link', { name: /sign in with hive/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the no-access screen when signed in without a Tome role (403)', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 403 })),
    )
    renderApp()
    expect(
      await screen.findByRole('heading', { name: /no access to tome/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the protected app once authenticated (200)', async () => {
    // Default handler reports a signed-in user with a Tome role.
    renderApp()
    expect(await screen.findByText('protected content')).toBeInTheDocument()
  })

  test('sign-out posts to /api/auth/logout and returns to the login screen', async () => {
    let loggedOut = false
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json(authenticatedUser)),
      http.post('/api/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp()

    await userEvent.click(
      await screen.findByRole('button', { name: /logout/i }),
    )

    await waitFor(() => expect(loggedOut).toBe(true))
    expect(
      await screen.findByRole('link', { name: /sign in with hive/i }),
    ).toBeInTheDocument()
  })
})
