import { http, HttpResponse, type RequestHandler } from 'msw'
import type { Me } from '@/api/schemas'
import { authenticatedUser, ruleSets } from './fixtures'

/**
 * Default MSW request handlers, shared by the test server (`server.ts`) and dev worker (`browser.ts`).
 * Defaults are a signed-in user with a Tome role; tests override per-case with `server.use(...)`
 * (e.g. a 401 for the unauthenticated path, a 403 for no-access).
 */

const makeAuthHandlers = (): RequestHandler[] => {
  const me: Me = { ...authenticatedUser }
  return [
    http.get('/api/auth/me', () => HttpResponse.json(me)),
    http.post(
      '/api/auth/logout',
      () => new HttpResponse(null, { status: 204 }),
    ),
  ]
}

const makeRuleSetHandlers = (): RequestHandler[] => [
  http.get('/api/rule-sets', () => HttpResponse.json(ruleSets)),
  // `GET /api/rule-sets/:id` now returns a summary (ADR-001) and the app no longer fetches it (the
  // sheet is a typed schema); a per-id handler is left out until something needs it.
]

export const handlers: RequestHandler[] = [
  ...makeAuthHandlers(),
  ...makeRuleSetHandlers(),
]
