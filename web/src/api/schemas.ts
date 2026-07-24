import { z } from 'zod'
import { apiRequest, type ApiRequestOptions } from './client'
import {
  meSchema,
  type Me,
} from '@/api/types'
import { ruleSetSummarySchema, type RuleSetSummary} from '@/types'

/**
 * Client for the `/api/auth` identity shapes and the `/api/rule-sets` picker shapes (openapi `Me`,
 * `RuleSetSummary`). No `SheetDefinition` fetch: the sheet is a typed client schema (ADR-001), so
 * `GET /rule-sets/{id}` returns only a summary.
 */

// ---- Auth ----
/** `GET /api/auth/me` — the session-bootstrap probe. The SessionProvider passes
 *  `redirectOnUnauthorized/notifyForbidden: false` to interpret 401/403 itself. */
export const getMe = (options: ApiRequestOptions = {}): Promise<Me> =>
  apiRequest('/auth/me', meSchema, options)

/** `POST /api/auth/logout` — clears the server session (204). */
export const logout = (): Promise<void> =>
  apiRequest('/auth/logout', z.undefined(), { method: 'POST' })

// ---- Rule sets ----
/** `GET /api/rule-sets` — the bundled rule sets (v1: just dnd35). */
export const listRuleSets = (signal?: AbortSignal): Promise<RuleSetSummary[]> =>
  apiRequest('/rule-sets', z.array(ruleSetSummarySchema), { signal })
