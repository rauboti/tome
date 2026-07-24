package no.rauboti.tome.rulesets

import no.rauboti.tome.characters.data.DnD35AbilityScores
import no.rauboti.tome.characters.data.DnD35CharacterBaseData
import no.rauboti.tome.characters.data.DnD35SkillRowInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test for the D&D 3.5 rule-set logic (T123 reshape). Pure — no Spring, no DB. Derived-value
 * computation moved onto the typed sheet (parity in `DnD35CharacterDataTest`), so the `RuleSet` now
 * carries only `id`/`name` and the soft [validate] over the typed [DnD35CharacterBaseData] (FR-005:
 * warnings only, never blocks).
 */
class DnD35RuleSetTest {
    private val ruleSet = DnD35RuleSet()

    @Test
    fun `id and name`() {
        assertEquals("dnd35", ruleSet.id())
        assertEquals("D&D 3.5", ruleSet.name())
    }

    @Test
    fun `validate flags an ability score below 1 against its field id`() {
        val warnings = ruleSet.validate(DnD35CharacterBaseData(abilities = DnD35AbilityScores(strength = 0, dexterity = 12)))
        assertEquals(1, warnings.size)
        assertEquals("ability.below-minimum", warnings.first().code)
        assertEquals("strength", warnings.first().field)
    }

    @Test
    fun `validate returns no warnings for a valid sheet`() {
        assertTrue(ruleSet.validate(DnD35CharacterBaseData(level = 1)).isEmpty())
    }

    @Test
    fun `validate never blocks - a default sheet is clean`() {
        assertTrue(ruleSet.validate(DnD35CharacterBaseData()).isEmpty())
    }

    @Test
    fun `validate warns when skill ranks exceed the 3-5 maximum for the level`() {
        val warnings =
            ruleSet.validate(
                DnD35CharacterBaseData(
                    level = 1, // class-skill max = level + 3 = 4
                    skills = listOf(DnD35SkillRowInput(skill = "Climb", keyAbility = "strMod", ranks = 8, classSkill = true)),
                ),
            )
        assertEquals(1, warnings.size)
        assertEquals("skill.ranks-exceed-max", warnings.first().code)
        assertEquals("skills", warnings.first().field)
    }

    @Test
    fun `validate does not warn for skill ranks within the maximum`() {
        val warnings =
            ruleSet.validate(
                DnD35CharacterBaseData(
                    level = 5, // class-skill max = 8
                    skills = listOf(DnD35SkillRowInput(skill = "Climb", keyAbility = "strMod", ranks = 8, classSkill = true)),
                ),
            )
        assertTrue(warnings.isEmpty())
    }
}
