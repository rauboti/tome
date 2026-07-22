import { z } from 'zod'
import { apiRequest } from './client'

/**
 * Typed client for the character endpoints (openapi `/characters`, US1). Zod validates every
 * response body; the functions wrap [apiRequest] with the right method/path. Sheet `data` is a flat,
 * rule-set-shaped object (its field ids come from the rule set's `SheetDefinition`), so it is typed
 * loosely as `Record<string, unknown>` — the definition-driven renderer, not this schema, knows the
 * per-field shapes. `data` carries the server-computed derived values on the way back.
 */

/** A sheet's values — a flat map keyed by field id (matches the api `SheetData`). */
export const sheetValuesSchema = z.record(z.string(), z.unknown())
export type SheetValues = z.infer<typeof sheetValuesSchema>

/** A soft validation finding (openapi `RuleWarning`): guidance, never a block (FR-005). `field` is
 *  the offending field id, or absent for a sheet-wide warning. */
export const ruleWarningSchema = z.object({
  code: z.string(),
  field: z.string().optional(),
  message: z.string(),
})
export type RuleWarning = z.infer<typeof ruleWarningSchema>

/** A character as shown in a list (openapi `CharacterSummary`). */
export const characterSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  ruleSetId: z.string(),
})
export type CharacterSummary = z.infer<typeof characterSummarySchema>

/** A full character (openapi `Character`): the summary plus owner, promoted HP (read from the sheet,
 *  nullable), the sheet `data` incl. computed derived values, soft `warnings`, and the `version` to
 *  echo back on the next write (optimistic concurrency, SC-006). */
export const characterSchema = characterSummarySchema.extend({
  ownerId: z.string(),
  hpCurrent: z.number().int().nullable().optional(),
  hpMax: z.number().int().nullable().optional(),
  data: sheetValuesSchema,
  warnings: z.array(ruleWarningSchema),
  version: z.number().int(),
})
export type Character = z.infer<typeof characterSchema>

/** `POST /api/characters` body — `ruleSetId` + `name` required, `data` an optional initial sheet. */
export type CreateCharacterInput = {
  ruleSetId: string
  name: string
  data?: SheetValues
}

/** `PUT /api/characters/{id}` body — the full sheet `data` + the read `version`; `name` optional
 *  (omit to keep the current name). */
export type UpdateCharacterInput = {
  name?: string
  data: SheetValues
  version: number
}

/** `GET /api/characters` — the caller's own characters. */
export const listCharacters = (
  signal?: AbortSignal,
): Promise<CharacterSummary[]> =>
  apiRequest('/characters', z.array(characterSummarySchema), { signal })

/** `GET /api/characters/{id}` — a single character with derived values + warnings. */
export const getCharacter = (
  id: string,
  signal?: AbortSignal,
): Promise<Character> =>
  apiRequest(`/characters/${id}`, characterSchema, { signal })

/** `POST /api/characters` — create a character for a rule set (201 → the created character). */
export const createCharacter = (
  input: CreateCharacterInput,
): Promise<Character> =>
  apiRequest('/characters', characterSchema, { method: 'POST', body: input })

/** `PUT /api/characters/{id}` — save the sheet with optimistic concurrency (409 on a stale version). */
export const updateCharacter = (
  id: string,
  input: UpdateCharacterInput,
): Promise<Character> =>
  apiRequest(`/characters/${id}`, characterSchema, {
    method: 'PUT',
    body: input,
  })

/** `DELETE /api/characters/{id}` — remove a character (204). */
export const deleteCharacter = (id: string): Promise<void> =>
  apiRequest(`/characters/${id}`, z.undefined(), { method: 'DELETE' })
