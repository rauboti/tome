import { z } from 'zod'
import { apiRequest } from './client'
import {
  characterSummarySchema,
  type CharacterSummary,
  type CreateCharacterInput,
  type UpdateCharacterInput,
} from '@/api/types'
import {characterSchema, type Character } from '@/types'

/**
 * Typed client for the character endpoints (openapi `/characters`). v1 ships D&D 3.5 only — the
 * `Sheet`/`SheetInput` aliases widen when Dark Souls lands.
 */

/** `GET /api/characters` — the caller's own characters. */
export const listCharacters = (
  signal?: AbortSignal,
): Promise<CharacterSummary[]> =>
  apiRequest('/characters', z.array(characterSummarySchema), { signal })

/** `GET /api/characters/{id}` — a single character with derived values + warnings. */
export const getCharacter = (id: string, signal?: AbortSignal): Promise<Character> =>
  // Zod validates the shape at runtime; the typed `Character` view narrows the carried table fields.
  apiRequest(`/characters/${id}`, characterSchema, {
    signal,
  }) as unknown as Promise<Character>

/** `POST /api/characters` — create a character for a rule set (201 → the created character). */
export const createCharacter = (input: CreateCharacterInput): Promise<Character> =>
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
