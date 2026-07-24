package no.rauboti.tome.rulesets

import no.rauboti.tome.characters.data.CharacterBaseData

/*
 * Core types of the rule-set engine (typed, ADR-001). A sheet is a typed
 * `CharacterBaseData`/`CharacterData` (characters/data) — not an untyped map + JSON definition. The
 * old data-driven shapes (`SheetData`/`SheetDefinition`/`SheetField`/`FieldType`) and the formula
 * engine (`SheetCompute`/`FormulaEvaluator`, `definition.json`) were retired in T125; a rule set now
 * supplies only identity + soft validation via [RuleSet], with all derived values as properties on the
 * typed sheet.
 */

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
 * The per-rule-set logic strategy (ADR-001). Resolved by [id] from the registry; unknown ids are
 * rejected. v1 ships only `DnD35RuleSet`.
 *
 * Derived-value computation lives on the typed sheet itself (`CharacterBaseData.enrich()` builds the
 * enriched [no.rauboti.tome.characters.data.CharacterData] whose properties are the derived values), so
 * this strategy carries only the rule set's identity and its soft validation.
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
