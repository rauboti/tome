import { z } from 'zod'
import { apiRequest, type ApiRequestOptions } from './client'

/**
 * Client for the `/api/auth` identity shapes and the `/api/rule-sets` picker shapes (openapi `Me`,
 * `RuleSetSummary`). No `SheetDefinition` fetch: the sheet is a typed client schema (ADR-001), so
 * `GET /rule-sets/{id}` returns only a summary.
 */

// ---- Auth ----

/** `GET /api/auth/me` (openapi). Roles/locale are permissive strings the app only displays; the
 *  admin/user gate is server-side (FR-024). Locale renders English when null or unsupported (FR-015,
 *  research D7). `displayName`/`locale` are `.nullish()`, not `.optional()`: the BFF serializes an
 *  absent claim as explicit `null`, which `.optional()` would reject. */
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
