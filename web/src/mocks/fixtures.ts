import type { Me, RuleSetSummary, SheetDefinition } from '@/api/schemas'

/**
 * Shared sample data for the MSW handlers (dev worker + test server). Shapes mirror the openapi
 * contract (`Me`, `RuleSetSummary`, `SheetDefinition`).
 */

/** The signed-in user returned by `GET /api/auth/me` in mock mode (no locale chosen → English UI,
 *  FR-015). Has a Tome role, so the app renders (roleless would be a 403 / no-access). */
export const authenticatedUser: Me = {
  userId: 'ada-lovelace',
  displayName: 'Ada Lovelace',
  roles: ['user'],
  // Explicit null, exactly as the BFF serializes a user with no locale set → English UI (FR-015).
  // (Guards the regression where the schema rejected `null` and dropped the app to the login screen.)
  locale: null,
}

/** The bundled rule sets (`GET /api/rule-sets`) — v1 ships D&D 3.5 only. */
export const ruleSets: RuleSetSummary[] = [{ id: 'dnd35', name: 'D&D 3.5' }]

/**
 * A compact D&D 3.5 sheet definition for mock mode (`GET /api/rule-sets/dnd35`). Enough shape to
 * exercise the renderer (one section per widget type + a derived field). The authoritative
 * definition is the api's `rulesets/dnd35/definition.json`; the real dev/prod stack serves that.
 */
export const dnd35Definition: SheetDefinition = {
  ruleSetId: 'dnd35',
  version: '1.0.0',
  sections: [
    {
      id: 'identity',
      labelKey: 'dnd35.section.identity',
      columns: 2,
      fields: [
        // Explicit `null`s mirror the BFF's serialization of absent optional field props (Kotlin
        // nullables) — guards the regression where the schema rejected `null` and the sheet failed
        // to load the rule-set definition.
        {
          id: 'name',
          labelKey: 'dnd35.field.name',
          type: 'text',
          derivedFrom: null,
          options: null,
        },
        { id: 'level', labelKey: 'dnd35.field.level', type: 'int' },
        {
          id: 'alignment',
          labelKey: 'dnd35.field.alignment',
          type: 'select',
          options: [
            { value: 'LG', labelKey: 'dnd35.alignment.LG' },
            { value: 'TN', labelKey: 'dnd35.alignment.TN' },
            { value: 'CE', labelKey: 'dnd35.alignment.CE' },
          ],
        },
      ],
    },
    {
      id: 'abilities',
      labelKey: 'dnd35.section.abilities',
      columns: 4,
      fields: [
        { id: 'strength', labelKey: 'dnd35.field.strength', type: 'int' },
        {
          id: 'strMod',
          labelKey: 'dnd35.field.strMod',
          type: 'derived',
          derivedFrom: 'floor((strength - 10) / 2)',
        },
      ],
    },
    {
      id: 'gear',
      labelKey: 'dnd35.section.gear',
      fields: [
        { id: 'feats', labelKey: 'dnd35.field.feats', type: 'list' },
        { id: 'notes', labelKey: 'dnd35.field.notes', type: 'text' },
      ],
    },
  ],
}
