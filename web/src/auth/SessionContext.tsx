/* eslint-disable react-refresh/only-export-components -- the provider and its `useSession` hook are
   one cohesive module; co-locating them only costs this file a full HMR reload. */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { ApiError, setOnForbidden } from '@/api/client'
import { getMe, logout, type Me } from '@/api/schemas'
import { applyLocale } from '@/i18n'

/** Session state, resolved once the `/api/auth/me` probe settles. `noAccess` is a signed-in Hive
 *  user with no Tome role (the api answers 403, FR-024) — distinct from `unauthenticated` (401). */
export type SessionState =
  | { status: 'loading'; user: null }
  | { status: 'authenticated'; user: Me }
  | { status: 'unauthenticated'; user: null }
  | { status: 'noAccess'; user: null }

type SessionContextValue = SessionState & {
  /** Re-run the session probe (e.g. after returning from the Hive callback). */
  reload: () => Promise<void>
  /** Clear the server session (`POST /api/auth/logout`) and drop to unauthenticated. */
  signOut: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

/**
 * Probe the session via `/api/auth/me` (200 authenticated, 401 unauthenticated, 403 no-access). The
 * locale is applied *before* the state lands so the first paint is in the right language (FR-015).
 * Opts out of the client's auto-redirect and global no-access handler to resolve 401/403 itself.
 */
const fetchSession = async (signal: AbortSignal): Promise<SessionState> => {
  try {
    const user = await getMe({
      redirectOnUnauthorized: false,
      notifyForbidden: false,
      signal,
    })
    await applyLocale(user.locale)
    return { status: 'authenticated', user }
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return { status: 'noAccess', user: null }
    }
    return { status: 'unauthenticated', user: null }
  }
}

export const SessionProvider = ({ children }: { children: ReactNode }) => {
  const [state, setState] = useState<SessionState>({
    status: 'loading',
    user: null,
  })

  useEffect(() => {
    const controller = new AbortController()
    void fetchSession(controller.signal).then((next) => {
      if (!controller.signal.aborted) setState(next)
    })
    // Any later data call that 403s (e.g. a role revoked mid-session) drops the whole app to the
    // no-access screen, no matter which request surfaced it.
    setOnForbidden(() => setState({ status: 'noAccess', user: null }))
    return () => {
      controller.abort()
      setOnForbidden(null)
    }
  }, [])

  const reload = useCallback(async () => {
    setState({ status: 'loading', user: null })
    setState(await fetchSession(new AbortController().signal))
  }, [])

  const signOut = useCallback(async () => {
    // Best-effort server logout; drop to unauthenticated regardless so the guard shows the login
    // screen (a dead session is already effectively logged out).
    try {
      await logout()
    } finally {
      setState({ status: 'unauthenticated', user: null })
    }
  }, [])

  return (
    <SessionContext value={{ ...state, reload, signOut }}>
      {children}
    </SessionContext>
  )
}

/** Access the current session. Throws if used outside a `<SessionProvider>`. */
export const useSession = (): SessionContextValue => {
  const value = useContext(SessionContext)
  if (value === null) {
    throw new Error('useSession must be used within a <SessionProvider>')
  }
  return value
}
