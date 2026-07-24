import { z } from 'zod'
import { apiRequest } from './client'
import {
  dnd35SheetSchema,
  type DnD35Sheet,
  type DnD35SheetInput,
} from '@/sheets/dnd35'

/**
 * Typed client for the character endpoints (openapi `/characters`, US1). Zod validates every response
 * body; the functions wrap [apiRequest] with the right method/path. Sheet `data` is the **typed**
 * rule-set sheet (ADR-001): a request sends the base [DnD35SheetInput]; a response carries the enriched
 * [DnD35Sheet] (base + derived). v1 ships D&D 3.5 only — the union widens when Dark Souls (US5) lands.
 */

/** The enriched sheet a response carries. v1: D&D 3.5. */
export type Sheet = DnD35Sheet
/** The base sheet a request sends. v1: D&D 3.5. */
export type SheetInput = DnD35SheetInput
export const sheetSchema = dnd35SheetSchema

/** A soft validation finding (openapi `RuleWarning`): guidance, never a block (FR-005). `field` is
 *  the offending field id, or `null` for a sheet-wide warning — the BFF serializes it from a Kotlin
 *  nullable, so `.nullish()` (not `.optional()`, which would reject the explicit `null`). */
export const ruleWarningSchema = z.object({
  code: z.string(),
  field: z.string().nullish(),
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

/** A full character (openapi `Character`): the summary plus owner, the enriched sheet `data` (base +
 *  computed derived values), soft `warnings`, and the `version` to echo back on the next write
 *  (optimistic concurrency, SC-006). HP lives inside `data.hitPoints` — no promoted top-level HP (ADR-001). */
export const characterSchema = characterSummarySchema.extend({
  ownerId: z.string(),
  data: sheetSchema,
  warnings: z.array(ruleWarningSchema),
  version: z.number().int(),
})
export type Character = Omit<z.infer<typeof characterSchema>, 'data'> & { data: Sheet }

/** `POST /api/characters` body — the promoted `name` and the typed base `data` (its `ruleSetId`
 *  selects the rule set; no separate top-level `ruleSetId`, ADR-001). */
export type CreateCharacterInput = {
  name: string
  data: SheetInput
}

/** `PUT /api/characters/{id}` body — the typed base sheet `data` (base inputs only; derived are
 *  recomputed on read, D8) + the read `version`; `name` optional (omit to keep the current name). */
export type UpdateCharacterInput = {
  name?: string
  data: SheetInput
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
  // Zod validates the shape at runtime; the typed `Character` view narrows the carried table fields.
  apiRequest(`/characters/${id}`, characterSchema, {
    signal,
  }) as unknown as Promise<Character>

/** `POST /api/characters` — create a character for a rule set (201 → the created character). */
export const createCharacter = (
  input: CreateCharacterInput,
): Promise<Character> =>
  apiRequest('/characters', characterSchema, {
    method: 'POST',
    body: input,
  }) as unknown as Promise<Character>

/** `PUT /api/characters/{id}` — save the sheet with optimistic concurrency (409 on a stale version). */
export const updateCharacter = (
  id: string,
  input: UpdateCharacterInput,
): Promise<Character> =>
  apiRequest(`/characters/${id}`, characterSchema, {
    method: 'PUT',
    body: input,
  }) as unknown as Promise<Character>

/** `DELETE /api/characters/{id}` — remove a character (204). */
export const deleteCharacter = (id: string): Promise<void> =>
  apiRequest(`/characters/${id}`, z.undefined(), { method: 'DELETE' })
