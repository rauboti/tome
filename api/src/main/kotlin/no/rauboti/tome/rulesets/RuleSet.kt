package no.rauboti.tome.rulesets

import no.rauboti.tome.characters.data.CharacterBaseData

/*
 * Core types of the rule-set engine. Post-ADR-001 (typed engine): the sheet is a typed
 * `CharacterBaseData`/`CharacterData` (characters/data), not an untyped map + JSON definition. The
 * `SheetData`/`SheetDefinition`/`SheetField`/`FieldType` types below are the **residual** data-driven
 * shapes, now used only by the not-yet-retired `SheetCompute`/`FormulaEvaluator` (deleted in T125);
 * the live `RuleSet` strategy no longer references them.
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
 * The per-rule-set logic strategy (research D3, reshaped for the typed engine — ADR-001). Resolved by
 * [id] from the registry; unknown ids are rejected. v1 ships only `DnD35RuleSet`.
 *
 * Derived-value computation has moved onto the typed sheet itself (`CharacterBaseData.enrich()` builds
 * the enriched [no.rauboti.tome.characters.data.CharacterData] whose properties are the derived values),
 * so this strategy no longer computes anything or exposes a data-driven definition. It carries only the
 * rule set's identity and its soft validation.
 */
interface RuleSet {
    /** The rule-set id this strategy handles, e.g. `dnd35`. Matches `CharacterBaseData.ruleSetId`. */
    fun id(): String

    /** Human-readable name for pickers/summaries, e.g. `D&D 3.5`. */
    fun name(): String

    /**
     * Soft-validate a stored sheet's base inputs and return any [RuleWarning]s (FR-005). **Never**
     * throws or blocks — an empty list means "no concerns". [sheet] is the typed base for this rule set
     * (an implementation validates only the variant it handles; others yield no warnings).
     */
    fun validate(sheet: CharacterBaseData): List<RuleWarning>
}
