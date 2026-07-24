import { z } from 'zod'
import { apiRequest, type ApiRequestOptions } from './client'

/**
 * Typed client for the Tome BFF: the `/api/auth` identity shapes and the `/api/rule-sets` picker
 * shapes (openapi `Me`, `RuleSetSummary`). Zod schemas validate every response body; the functions
 * wrap [apiRequest] with the right method/path.
 *
 * ADR-001: the sheet is a typed schema known to the client (see `@/sheets/dnd35`), not a data-driven
 * `SheetDefinition` fetched to drive a generic renderer — so there is no definition schema or
 * `GET /rule-sets/{id}` definition fetch here anymore (that endpoint returns a summary now).
 */

// ---- Auth ----

/** `GET /api/auth/me` (openapi). Roles/locale stay permissive strings — the app only displays
 *  them; the admin/user gate is enforced server-side (FR-024). Locale renders English when null or
 *  unsupported (FR-015, research D7).
 *
 *  `displayName`/`locale` are `.nullish()` (string | null | undefined), not `.optional()`: the BFF
 *  serializes an absent claim as an explicit JSON `null` (e.g. `"locale":null`), and `.optional()`
 *  accepts a *missing* key but rejects `null` — which made a good 200 throw a ZodError. */
export const meSchema = z.object({
  userId: z.string(),
  displayName: z.string().nullish(),
  roles: z.array(z.string()),
  locale: z.string().nullish(),
})
export type Me = z.infer<typeof meSchema>

/** `GET /api/auth/me` — the session-bootstrap probe. The SessionProvider passes
 *  `redirectOnUnauthorized/notifyForbidden: false` to interpret 401/403 itself. */
export const getMe = (options: ApiRequestOptions = {}): Promise<Me> =>
  apiRequest('/auth/me', meSchema, options)

/** `POST /api/auth/logout` — clears the server session (204). */
export const logout = (): Promise<void> =>
  apiRequest('/auth/logout', z.undefined(), { method: 'POST' })

// ---- Rule sets ----

/** A rule set as shown in a picker (openapi `RuleSetSummary`). */
export const ruleSetSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
})
export type RuleSetSummary = z.infer<typeof ruleSetSummarySchema>

/** `GET /api/rule-sets` — the bundled rule sets (v1: just dnd35). */
export const listRuleSets = (signal?: AbortSignal): Promise<RuleSetSummary[]> =>
  apiRequest('/rule-sets', z.array(ruleSetSummarySchema), { signal })
