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
 * A character's/NPC's sheet values: a flat JSON object keyed by field id (matching the field ids in
 * the [SheetDefinition]). Stored as Postgres `JSONB` and (de)serialized by Jackson via the JdbcTemplate
 * conversion in JsonbSupport (T014). Values are the natural JSON types — Int/String/Boolean/List/Map —
 * so the engine and rule-set logic read them dynamically. Derived fields are written back into the same
 * map by [RuleSet.computeDerived].
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

/** A titled group of fields on the sheet. [labelKey] is an i18n key resolved by the web. */
data class SheetSection(
    val id: String,
    val labelKey: String,
    val fields: List<SheetField>,
)

/**
 * One field on the sheet. [type] is one of [FieldType] (kept a plain string so the definition stays
 * pure data — the openapi contract types it as a string enum). [derivedFrom] is set for
 * [FieldType.DERIVED] fields (a formula reference the rule-set logic computes); [options] lists the
 * choices for a [FieldType.SELECT] field.
 */
data class SheetField(
    val id: String,
    val labelKey: String,
    val type: String,
    val derivedFrom: String? = null,
    val options: List<FieldOption>? = null,
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
