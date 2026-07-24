/**
 * Typed D&D 3.5 sheet (ADR-001) — the web mirror of the Kotlin base/enriched split. See web/README.md
 * "The typed sheet mirror". Base inputs are the source of truth; `enrichDnD35` recomputes derived values
 * for live editing and they are never sent back. Hand-authored to track the openapi `Sheet`/`SheetInput`.
 *
 * Split across this folder: `types` (shapes), `schema` (Zod validation of the enriched response),
 * `enrich` (base → enriched + per-row derived), `presets` (option lists, canonical skills, sheet
 * factory/strip). This barrel is the public entry point — import from `@/sheets/dnd35`.
 */
export * from './types'
export * from './schema'
export * from './enrich'
export * from './presets'
