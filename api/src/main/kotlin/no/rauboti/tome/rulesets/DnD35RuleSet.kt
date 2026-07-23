package no.rauboti.tome.rulesets

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * The D&D 3.5 rule-set logic (research D3). The sheet *structure* is data ([definition], loaded from
 * the bundled `rulesets/dnd35/definition.json`); this class is the *logic* half — it computes derived
 * values and raises soft validation warnings. Registered as the `dnd35` [RuleSet] by the registry (T019).
 *
 * [computeDerived] is **definition-driven** (T105): it delegates to the shared [SheetCompute], which
 * evaluates every `derivedFrom` formula in the [definition] via [FormulaEvaluator] — ability modifiers,
 * saves, initiative today, and any per-row table totals a later definition adds — identically to the
 * web `derive.ts`. No derived value is hand-computed here (all v1 derived are formula-expressible).
 * [validate] remains rule-set logic (soft warnings, FR-005).
 */
@Component
class DnD35RuleSet(
    objectMapper: ObjectMapper,
) : RuleSet {
    private val sheetDefinition: SheetDefinition =
        ClassPathResource(DEFINITION_PATH).inputStream.use { objectMapper.readValue(it, SheetDefinition::class.java) }

    override fun id(): String = RULE_SET_ID

    override fun name(): String = "D&D 3.5"

    override fun definition(): SheetDefinition = sheetDefinition

    /**
     * Recompute the derived values from the base inputs and return a **new** sheet (the input is never
     * mutated). Missing/blank inputs read as 0 so a partially-filled sheet still computes cleanly.
     * The actual work is the shared, definition-driven [SheetCompute] over this rule set's formulas.
     */
    override fun computeDerived(data: SheetData): SheetData = SheetCompute.resolve(sheetDefinition, data)

    /**
     * Soft-validate the sheet (FR-005): guidance only, never blocks — always returns a list. [change]
     * is part of the contract for scoping warnings to a write; v1 checks the current sheet as a whole.
     * Only checks fields that are actually present, so a partial sheet isn't spuriously flagged.
     */
    override fun validate(
        data: SheetData,
        change: SheetChange,
    ): List<RuleWarning> {
        val warnings = mutableListOf<RuleWarning>()
        for (ability in ABILITIES) {
            if (data.containsKey(ability) && intOf(data, ability) < MIN_ABILITY_SCORE) {
                warnings +=
                    RuleWarning(
                        code = "ability.below-minimum",
                        message = "Ability score for '$ability' is below the minimum of $MIN_ABILITY_SCORE.",
                        field = ability,
                    )
            }
        }
        if (data.containsKey("level") && intOf(data, "level") < MIN_LEVEL) {
            warnings += RuleWarning("level.below-minimum", "Level must be at least $MIN_LEVEL.", "level")
        }
        // Soft-check skill ranks against the 3.5 maximum: level + 3 for a class skill, half that for a
        // cross-class skill (SRD). Only when level is known; a rankless (preset-only) row isn't flagged.
        if (data.containsKey("level")) {
            val level = intOf(data, "level")
            for (row in data["skills"] as? List<*> ?: emptyList<Any?>()) {
                val skill = row as? Map<*, *> ?: continue
                val ranks = (skill["ranks"] as? Number)?.toInt() ?: continue
                val isClassSkill = skill["classSkill"] as? Boolean ?: false
                val maxRanks = if (isClassSkill) level + MAX_RANKS_OVER_LEVEL else (level + MAX_RANKS_OVER_LEVEL) / 2
                if (ranks > maxRanks) {
                    val name = (skill["skill"] as? String) ?: "skill"
                    warnings +=
                        RuleWarning(
                            code = "skill.ranks-exceed-max",
                            message = "Ranks in '$name' ($ranks) exceed the maximum of $maxRanks at level $level.",
                            field = "skills",
                        )
                }
            }
        }
        return warnings
    }

    /** Read a sheet value as an Int; absent/null/non-numeric reads as 0. */
    private fun intOf(
        data: SheetData,
        key: String,
    ): Int = (data[key] as? Number)?.toInt() ?: 0

    companion object {
        const val RULE_SET_ID = "dnd35"
        private const val DEFINITION_PATH = "rulesets/dnd35/definition.json"
        private const val MIN_ABILITY_SCORE = 1
        private const val MIN_LEVEL = 1

        /** Max skill ranks over character level: class skill = level + 3, cross-class = (level + 3) / 2. */
        private const val MAX_RANKS_OVER_LEVEL = 3
        private val ABILITIES =
            listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
    }
}
