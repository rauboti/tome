import { z } from 'zod'
import { apiRequest, type ApiRequestOptions } from './client'

/**
 * Typed client for the Tome BFF: the `/api/auth` identity shapes and the `/api/rule-sets` engine
 * shapes (openapi `Me`, `RuleSetSummary`, `SheetDefinition`). Zod schemas validate every response
 * body; the functions wrap [apiRequest] with the right method/path so callers work in typed values.
 */

// ---- Auth ----

/** `GET /api/auth/me` (openapi). Roles/locale stay permissive strings — the app only displays
 *  them; the Admin/User gate is enforced server-side (FR-024). Locale renders English when null or
 *  unsupported (FR-015, research D7). */
export const meSchema = z.object({
  userId: z.string(),
  displayName: z.string().optional(),
  roles: z.array(z.string()),
  locale: z.string().optional(),
})
export type Me = z.infer<typeof meSchema>

/** `GET /api/auth/me` — the session-bootstrap probe. The SessionProvider passes
 *  `redirectOnUnauthorized/notifyForbidden: false` to interpret 401/403 itself. */
export const getMe = (options: ApiRequestOptions = {}): Promise<Me> =>
  apiRequest('/auth/me', meSchema, options)

/** `POST /api/auth/logout` — clears the server session (204). */
export const logout = (): Promise<void> =>
  apiRequest('/auth/logout', z.undefined(), { method: 'POST' })

// ---- Rule sets (Hybrid engine) ----

/** A rule set as shown in a picker (openapi `RuleSetSummary`). */
export const ruleSetSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
})
export type RuleSetSummary = z.infer<typeof ruleSetSummarySchema>

/** A choice for a `select` field. */
export const fieldOptionSchema = z.object({
  value: z.string(),
  labelKey: z.string(),
})

/** One field on the sheet (openapi `SheetDefinition.sections[].fields[]`). `type` is a string
 *  enum; `derivedFrom` is present for derived fields, `options` for selects. */
export const sheetFieldSchema = z.object({
  id: z.string(),
  labelKey: z.string(),
  type: z.enum(['int', 'text', 'bool', 'select', 'list', 'derived']),
  derivedFrom: z.string().optional(),
  options: z.array(fieldOptionSchema).optional(),
})
export type SheetField = z.infer<typeof sheetFieldSchema>

export const sheetSectionSchema = z.object({
  id: z.string(),
  labelKey: z.string(),
  fields: z.array(sheetFieldSchema),
})
export type SheetSection = z.infer<typeof sheetSectionSchema>

/** The data-driven sheet schema (openapi `SheetDefinition`) that drives the generic renderer. */
export const sheetDefinitionSchema = z.object({
  ruleSetId: z.string(),
  version: z.string(),
  sections: z.array(sheetSectionSchema),
})
export type SheetDefinition = z.infer<typeof sheetDefinitionSchema>

/** `GET /api/rule-sets` — the bundled rule sets (v1: just dnd35). */
export const listRuleSets = (signal?: AbortSignal): Promise<RuleSetSummary[]> =>
  apiRequest('/rule-sets', z.array(ruleSetSummarySchema), { signal })

/** `GET /api/rule-sets/{id}` — the full sheet definition for a rule set. */
export const getRuleSetDefinition = (
  id: string,
  signal?: AbortSignal,
): Promise<SheetDefinition> =>
  apiRequest(`/rule-sets/${id}`, sheetDefinitionSchema, { signal })
