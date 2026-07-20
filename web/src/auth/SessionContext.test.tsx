import { describe, expect, test, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { ThemeProvider } from '@rauboti/ui'
import { SessionProvider, useSession } from './SessionContext'
import { RequireAuth } from './RequireAuth'

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

/** Stub `fetch` with per-endpoint responses. `me` is a Response or a never-resolving promise. */
const stubFetch = (me: Response | 'pending', onLogout?: () => void) => {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/auth/logout')) {
        onLogout?.()
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.endsWith('/api/auth/me')) {
        return me === 'pending'
          ? new Promise<Response>(() => {})
          : Promise.resolve(me)
      }
      throw new Error(`unexpected fetch: ${init?.method ?? 'GET'} ${url}`)
    }),
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

const jsonResponse = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })

describe('session + RequireAuth', () => {
  test('shows a loading status while the probe is in flight', () => {
    stubFetch('pending')
    renderApp()
    expect(
      screen.getByRole('status', { name: /checking your session/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the login screen when unauthenticated (401)', async () => {
    stubFetch(jsonResponse(401, { title: 'Unauthorized' }))
    renderApp()
    expect(
      await screen.findByRole('link', { name: /sign in with hive/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the no-access screen when signed in without a Tome role (403)', async () => {
    stubFetch(jsonResponse(403, { title: 'Forbidden' }))
    renderApp()
    expect(
      await screen.findByRole('heading', { name: /no access to tome/i }),
    ).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  test('renders the protected app once authenticated (200)', async () => {
    stubFetch(
      jsonResponse(200, {
        userId: 'u-1',
        displayName: 'Ada',
        roles: ['User'],
      }),
    )
    renderApp()
    expect(await screen.findByText('protected content')).toBeInTheDocument()
  })

  test('sign-out posts to /api/auth/logout and returns to the login screen', async () => {
    let loggedOut = false
    stubFetch(jsonResponse(200, { userId: 'u-1', roles: ['User'] }), () => {
      loggedOut = true
    })
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
