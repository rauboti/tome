package no.rauboti.tome.rulesets

/*
 * Core types of the Hybrid rule-set engine (research D3). A rule set is defined in two halves:
 *  - data  — a SheetDefinition (authored as JSON, e.g. resources/rulesets/dnd35/definition.json)
 *    that the web renders generically; and
 *  - logic — a RuleSet strategy that computes derived values and raises soft validation warnings.
 *
 * Cross-cutting services (characters, npcs, campaigns, combat, dice, realtime, permissions) depend
 * only on SheetData + RuleSet, never on a concrete rule set — so a second/third rule set is just a
 * new definition + logic, with no change to the shared engine (FR-023/SC-009).
 */

/**
 * A character's/NPC's sheet values: a flat map keyed by field id (matching the field ids in the
 * [SheetDefinition]). Persisted natively as a BSON sub-document on the character document (no JSON-string
 * glue). Values are the natural JSON/BSON types — Int/String/Boolean/List/Map — so the engine and
 * rule-set logic read them dynamically. Only base inputs are stored; derived fields are recomputed on
 * read into the resolved map by [RuleSet.computeDerived] (compute-on-read, D8), never persisted.
 */
typealias SheetData = Map<String, Any?>

/**
 * The delta being written, passed to [RuleSet.validate] so it can scope warnings to what actually
 * changed (e.g. warn on a just-raised ability score, not the whole sheet). On create, [previous] is
 * empty and [changedFields] is every field present; on edit, [previous] is the stored sheet and
 * [changedFields] the ids whose value differs.
 */
data class SheetChange(
    val previous: SheetData,
    val changedFields: Set<String>,
)

/**
 * A soft validation finding (FR-005): guidance, never a hard block — the DM can always override.
 * [field] is the offending field id, or null for a sheet-wide warning. Serialized to the openapi
 * `RuleWarning` schema ({ code, field, message }).
 */
data class RuleWarning(
    val code: String,
    val message: String,
    val field: String? = null,
)

/**
 * The data-driven sheet schema the web renders (openapi `SheetDefinition`). [version] lets a
 * definition evolve independently of the rule-set code; [sections] group the fields for layout.
 */
data class SheetDefinition(
    val ruleSetId: String,
    val version: String,
    val sections: List<SheetSection>,
)

/**
 * A titled group of fields on the sheet. [labelKey] is an i18n key resolved by the web. [columns] is
 * the number of columns the web lays the section's fields out in (null → 1, i.e. one field per row);
 * a field can span several of them via [SheetField.colSpan]. Layout only — the engine ignores it.
 */
data class SheetSection(
    val id: String,
    val labelKey: String,
    val fields: List<SheetField>,
    val columns: Int? = null,
)

/**
 * One field on the sheet. [type] is one of [FieldType] (kept a plain string so the definition stays
 * pure data — the openapi contract types it as a string enum). [derivedFrom] is set for
 * [FieldType.DERIVED] fields (a formula reference the rule-set logic computes); [options] lists the
 * choices for a [FieldType.SELECT] field. [colSpan] is how many of the section's [SheetSection.columns]
 * this field occupies (null → 1) — e.g. a full-width field in a 2-column section uses `colSpan: 2`.
 */
data class SheetField(
    val id: String,
    val labelKey: String,
    val type: String,
    val derivedFrom: String? = null,
    val options: List<FieldOption>? = null,
    val colSpan: Int? = null,
    // For a [FieldType.TABLE] field only: the per-row column definitions (each itself a [SheetField];
    // one level deep — no nested tables) and, optionally, fixed rows the definition seeds (canonical
    // content, e.g. the 3.5 skill list). A table row's value in the sheet is a map keyed by column id.
    val columns: List<SheetField>? = null,
    val presetRows: List<Map<String, Any?>>? = null,
    // For a `select` field/column whose choices come from a named catalog filtered by another field's
    // value (e.g. spells on the caster's class list, T113/T114). Mutually exclusive with static [options];
    // the web fetches the choices from the catalog endpoint. Rule-set-agnostic mechanism.
    val optionsFrom: OptionsFrom? = null,
)

/**
 * Points a `select` at a named [no.rauboti.tome.catalogs.Catalog] filtered by another field's value:
 * the picker shows `catalog` options for the current value of the `filterBy` field (e.g.
 * `{ catalog: "spells", filterBy: "casterClass" }`). Openapi `SheetField.optionsFrom`.
 */
data class OptionsFrom(
    val catalog: String,
    val filterBy: String,
)

/** A choice for a `select` field: the stored [value] plus its i18n [labelKey]. */
data class FieldOption(
    val value: String,
    val labelKey: String,
)

/**
 * The field-type vocabulary shared by the definition, the rule-set logic, and (mirrored) the web
 * renderer. Plain string constants rather than an enum so [SheetField.type] serializes exactly as the
 * contract's lowercase values with no Jackson enum mapping.
 */
object FieldType {
    const val INT = "int"
    const val TEXT = "text"
    const val BOOL = "bool"
    const val SELECT = "select"
    const val LIST = "list"
    const val DERIVED = "derived"
    const val TABLE = "table"
}

/**
 * The per-rule-set logic strategy (research D3). Resolved by [id] from the registry (T019); unknown
 * ids are rejected. v1 ships only `DnD35RuleSet` (T017).
 */
interface RuleSet {
    /** The rule-set id this strategy handles, e.g. `dnd35`. Matches `SheetDefinition.ruleSetId`. */
    fun id(): String

    /** Human-readable name for pickers/summaries, e.g. `D&D 3.5`. */
    fun name(): String

    /** The sheet definition (data) this rule set renders and validates against. */
    fun definition(): SheetDefinition

    /**
     * Recompute every derived value from the base inputs and return a new [SheetData] with those
     * values filled in (e.g. 3.5 ability modifiers, saves, BAB). Pure: no I/O, no mutation of [data].
     */
    fun computeDerived(data: SheetData): SheetData

    /**
     * Soft-validate a write and return any [RuleWarning]s (FR-005). **Never** throws or blocks — an
     * empty list means "no concerns". [change] describes what the write touched so validation can
     * focus its warnings.
     */
    fun validate(
        data: SheetData,
        change: SheetChange,
    ): List<RuleWarning>
}
