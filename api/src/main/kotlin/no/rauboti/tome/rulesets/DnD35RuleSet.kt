package no.rauboti.tome.rulesets

import no.rauboti.tome.characters.data.CharacterBaseData
import no.rauboti.tome.characters.data.DnD35CharacterBaseData
import org.springframework.stereotype.Component

/**
 * The D&D 3.5 rule-set logic (ADR-001). Derived values now live on the typed sheet
 * ([no.rauboti.tome.characters.data.DnD35CharacterData], built by `enrich()`), so this strategy is
 * purely identity + soft validation — no definition, no `computeDerived`. Registered as the `dnd35`
 * [RuleSet] by the registry.
 */
@Component
class DnD35RuleSet : RuleSet {
    override fun id(): String = RULE_SET_ID

    override fun name(): String = "D&D 3.5"

    /**
     * Soft-validate the 3.5 base inputs (FR-005): guidance only, never blocks — always returns a list.
     * Only handles [DnD35CharacterBaseData]; any other variant yields no warnings. Checks: ability
     * scores and level at/above their minimums, and skill ranks within the 3.5 max for the level
     * (class skill = level + 3, cross-class = half that).
     */
    override fun validate(sheet: CharacterBaseData): List<RuleWarning> {
        val s = sheet as? DnD35CharacterBaseData ?: return emptyList()
        val warnings = mutableListOf<RuleWarning>()

        val abilityScores =
            listOf(
                "strength" to s.abilities.strength,
                "dexterity" to s.abilities.dexterity,
                "constitution" to s.abilities.constitution,
                "intelligence" to s.abilities.intelligence,
                "wisdom" to s.abilities.wisdom,
                "charisma" to s.abilities.charisma,
            )
        for ((name, score) in abilityScores) {
            if (score < MIN_ABILITY_SCORE) {
                warnings +=
                    RuleWarning(
                        code = "ability.below-minimum",
                        message = "Ability score for '$name' is below the minimum of $MIN_ABILITY_SCORE.",
                        field = name,
                    )
            }
        }

        if (s.level < MIN_LEVEL) {
            warnings += RuleWarning("level.below-minimum", "Level must be at least $MIN_LEVEL.", "level")
        }

        // Soft-check skill ranks against the 3.5 maximum: level + 3 for a class skill, half that for a
        // cross-class skill (SRD). Preset/rankless rows sit well under the cap and aren't flagged.
        for (row in s.skills) {
            val maxRanks = if (row.classSkill) s.level + MAX_RANKS_OVER_LEVEL else (s.level + MAX_RANKS_OVER_LEVEL) / 2
            if (row.ranks > maxRanks) {
                val skillName = row.skill.ifBlank { "skill" }
                warnings +=
                    RuleWarning(
                        code = "skill.ranks-exceed-max",
                        message = "Ranks in '$skillName' (${row.ranks}) exceed the maximum of $maxRanks at level ${s.level}.",
                        field = "skills",
                    )
            }
        }
        return warnings
    }

    companion object {
        const val RULE_SET_ID = "dnd35"
        private const val MIN_ABILITY_SCORE = 1
        private const val MIN_LEVEL = 1

        /** Max skill ranks over character level: class skill = level + 3, cross-class = (level + 3) / 2. */
        private const val MAX_RANKS_OVER_LEVEL = 3
    }
}
