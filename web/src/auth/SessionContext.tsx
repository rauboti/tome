/* eslint-disable react-refresh/only-export-components -- the provider and its `useSession` hook are
   one cohesive module; co-locating them (plus the tiny path/schema exports) only costs this file a
   full HMR reload. */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { z } from 'zod'

/** The BFF login entry point — a full-page navigation (it 302s to Hive, so not a client route). */
export const LOGIN_PATH = '/auth/login'

/** `GET /api/auth/me` payload (openapi). Roles/locale stay permissive strings — the app only
 *  displays them; the Admin/User gate is enforced server-side (FR-024). */
export const meSchema = z.object({
  userId: z.string(),
  displayName: z.string().optional(),
  roles: z.array(z.string()),
  locale: z.string().optional(),
})
export type Me = z.infer<typeof meSchema>

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
 * Probe `GET /api/auth/me`. The BFF holds the Hive token server-side, so the browser sends only its
 * session cookie (`credentials: 'include'`). 200 → signed in with a Tome role; 401 → not signed in;
 * 403 → signed in but no Tome role (no-access). Any other failure is treated as unauthenticated so
 * the guard falls back to the login screen rather than a blank app.
 */
const fetchSession = async (signal: AbortSignal): Promise<SessionState> => {
  try {
    const res = await fetch('/api/auth/me', {
      credentials: 'include',
      signal,
    })
    if (res.status === 401) return { status: 'unauthenticated', user: null }
    if (res.status === 403) return { status: 'noAccess', user: null }
    if (!res.ok) return { status: 'unauthenticated', user: null }
    return { status: 'authenticated', user: meSchema.parse(await res.json()) }
  } catch {
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
    return () => controller.abort()
  }, [])

  const reload = useCallback(async () => {
    setState({ status: 'loading', user: null })
    setState(await fetchSession(new AbortController().signal))
  }, [])

  const signOut = useCallback(async () => {
    // Best-effort server logout; drop to unauthenticated regardless so the guard shows the login
    // screen (a dead session is already effectively logged out).
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      })
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
