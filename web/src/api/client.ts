import { z } from 'zod'

/** Backend endpoint that begins the Hive OAuth flow (302 → Hive authorize). A full-page
 *  navigation, not XHR — it can't be a client route. */
export const LOGIN_PATH = '/auth/login'

/** All BFF endpoints live under this prefix; callers pass resource-relative paths
 *  (e.g. `/auth/me`, `/rule-sets`). The dev proxy / nginx forwards `/api` to the api service. */
const API_BASE = '/api'

/** RFC-7807 problem details (openapi `Problem`). Every field is optional so an unexpected error
 *  body still parses. */
export const problemSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
})
export type Problem = z.infer<typeof problemSchema>

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

/**
 * Registered by the SessionProvider: called whenever a request comes back 403 (authenticated but
 * no Tome role), so the app can drop to the "no access to Tome" screen no matter which data call
 * surfaced it (FR-024). `null` clears the handler. The session-bootstrap probe opts out via
 * `notifyForbidden: false` (it interprets its own 403).
 */
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

/**
 * Typed fetch wrapper for the Tome BFF. Sends the session cookie, validates the response body
 * against `schema`, and normalizes failures:
 *   - 401 → redirect to `/auth/login` (unless opted out), then reject with `ApiError`.
 *   - 403 → notify the no-access handler (unless opted out), then reject with `ApiError`.
 *   - other non-2xx → reject with `ApiError` (parsing `problem+json` when present).
 *   - 2xx → parse the JSON body with `schema` (use `z.undefined()` for 204).
 */
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
