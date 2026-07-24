import { z } from 'zod'
import { problemSchema, type Problem } from './types'

/** Backend endpoint that begins the Hive OAuth flow (302 → Hive authorize). A full-page
 *  navigation, not XHR — it can't be a client route. */
export const LOGIN_PATH = '/auth/login'

/** All BFF endpoints live under this prefix; callers pass resource-relative paths
 *  (e.g. `/auth/me`, `/rule-sets`). The dev proxy / nginx forwards `/api` to the api service. */
const API_BASE = '/api'

/** Thrown for any non-2xx response. Carries the HTTP status and, when the body was
 *  `application/problem+json`, the parsed problem details. */
export class ApiError extends Error {
  readonly status: number
  readonly problem?: Problem

  constructor(status: number, problem?: Problem, message?: string) {
    super(
      message ??
        problem?.detail ??
        problem?.title ??
        `API request failed (${status})`,
    )
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

/** Global 403 handler: the SessionProvider registers it so any data call's 403 drops the app
 *  to the no-access screen. `null` clears it; the bootstrap probe opts out (`notifyForbidden: false`). */
let onForbidden: (() => void) | null = null
export const setOnForbidden = (handler: (() => void) | null): void => {
  onForbidden = handler
}

export type ApiRequestOptions = {
  method?: string
  /** Serialized to JSON and sent with `Content-Type: application/json`. */
  body?: unknown
  signal?: AbortSignal
  /** On 401, redirect the browser to Hive login (default). Pass `false` for the
   *  session-bootstrap probe so the app can render a login screen instead. */
  redirectOnUnauthorized?: boolean
  /** On 403, invoke the global no-access handler (default). Pass `false` for the
   *  bootstrap probe, which resolves its own no-access state. */
  notifyForbidden?: boolean
}

const readProblem = async (
  response: Response,
): Promise<Problem | undefined> => {
  if (!response.headers.get('content-type')?.includes('json')) return undefined
  try {
    return problemSchema.parse(await response.json())
  } catch {
    return undefined
  }
}

/** Typed fetch wrapper for the Tome BFF: sends the session cookie, validates the 2xx body against
 *  `schema` (`z.undefined()` for 204), and rejects non-2xx with `ApiError`. See the web README's Auth
 *  flow for the 401/403 handling. */
export const apiRequest = async <T>(
  path: string,
  schema: z.ZodType<T>,
  options: ApiRequestOptions = {},
): Promise<T> => {
  const {
    method,
    body,
    signal,
    redirectOnUnauthorized = true,
    notifyForbidden = true,
  } = options

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(`${API_BASE}${path}`, {
    method: method ?? (body !== undefined ? 'POST' : 'GET'),
    headers,
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  })

  if (response.status === 401) {
    const problem = await readProblem(response)
    if (redirectOnUnauthorized) window.location.assign(LOGIN_PATH)
    throw new ApiError(401, problem)
  }

  if (response.status === 403) {
    const problem = await readProblem(response)
    if (notifyForbidden) onForbidden?.()
    throw new ApiError(403, problem)
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }

  const data =
    response.status === 204 || response.headers.get('content-length') === '0'
      ? undefined
      : await response.json()

  return schema.parse(data)
}
