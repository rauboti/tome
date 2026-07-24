import { dnd35SheetSchema } from '@/sheets/dnd35'
import type { DnD35CharacterBaseData, DnD35CharacterData } from '@/types'

/** The enriched sheet a response carries. v1: D&D 3.5. */
export type Sheet = DnD35CharacterData
/** The base sheet a request sends. v1: D&D 3.5. */
export type SheetInput = DnD35CharacterBaseData


export const sheetSchema = dnd35SheetSchema

/** `POST /api/characters` body — the promoted `name` and the typed base `data` (its `ruleSetId`
 *  selects the rule set; no separate top-level `ruleSetId`). */
export type CreateCharacterInput = {
  name: string
  data: SheetInput
}

/** `PUT /api/characters/{id}` body — the typed base sheet `data` (base inputs only; derived are
 *  recomputed on read) + the read `version`; `name` optional (omit to keep the current name). */
export type UpdateCharacterInput = {
  name?: string
  data: SheetInput
  version: number
}
