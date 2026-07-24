import type { Me, RuleSetSummary } from '@/api/schemas'

/**
 * Shared sample data for the MSW handlers (dev worker + test server). Shapes mirror the openapi
 * contract (`Me`, `RuleSetSummary`). The sheet is a typed schema known to the client (ADR-001), so
 * there is no `SheetDefinition` fixture — character/sheet data is supplied per-test.
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
