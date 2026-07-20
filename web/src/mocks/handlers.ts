import { http, HttpResponse, type RequestHandler } from 'msw'
import type { Me } from '@/api/schemas'
import { authenticatedUser, dnd35Definition, ruleSets } from './fixtures'

/**
 * Default MSW request handlers, shared by the test server (`server.ts`) and the dev worker
 * (`browser.ts`). Defaults represent a signed-in user with a Tome role so the app renders without
 * a real Hive; individual tests override per-case with `server.use(...)` (e.g. a 401 for the
 * unauthenticated path, or a 403 for the no-access path).
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
  http.get('/api/rule-sets/:id', ({ params }) => {
    const { id } = params as { id: string }
    if (id !== 'dnd35') {
      return HttpResponse.json(
        { title: 'Not Found', status: 404 },
        {
          status: 404,
          headers: { 'Content-Type': 'application/problem+json' },
        },
      )
    }
    return HttpResponse.json(dnd35Definition)
  }),
]

export const handlers: RequestHandler[] = [
  ...makeAuthHandlers(),
  ...makeRuleSetHandlers(),
]
