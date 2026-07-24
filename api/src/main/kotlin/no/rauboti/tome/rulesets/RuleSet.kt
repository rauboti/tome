package no.rauboti.tome.rulesets

import no.rauboti.tome.characters.data.CharacterBaseData

/**
 * A soft validation finding: guidance, never a hard block — the DM can always override. [field] is
 * the offending field id, or null for a sheet-wide warning. Serialized to the openapi `RuleWarning`
 * schema.
 */
data class RuleWarning(
    val code: String,
    val message: String,
    val field: String? = null,
)

/**
 * The per-rule-set logic strategy, carrying only identity + soft validation — derived values live on
 * the typed sheet itself (`CharacterBaseData.enrich()`). Resolved by [id] from the registry (unknown
 * ids rejected); v1 ships only `DnD35RuleSet`.
 */
interface RuleSet {
    /** The rule-set id this strategy handles, e.g. `dnd35`. Matches `CharacterBaseData.ruleSetId`. */
    fun id(): String

    /** Human-readable name for pickers/summaries, e.g. `D&D 3.5`. */
    fun name(): String

    /**
     * Soft-validate a stored sheet's base inputs and return any [RuleWarning]s. **Never** throws or
     * blocks — an empty list means "no concerns". [sheet] is the typed base for this rule set (an
     * implementation validates only the variant it handles; others yield no warnings).
     */
    fun validate(sheet: CharacterBaseData): List<RuleWarning>
}
