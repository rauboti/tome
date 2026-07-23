package no.rauboti.tome.rulesets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Unit test for the D&D 3.5 rule-set logic (T016, written before the impl in T017). Pure — no Spring,
 * no DB: the rule set is constructed with a bare Jackson mapper and reads its bundled
 * `definition.json` off the classpath.
 *
 * `computeDerived` covers **ability modifiers, saves, and initiative**. (BAB was reduced to a plain
 * player-entered `int` in the T015 definition revision — see the "Deferred Decisions" note in plan.md
 * — so it is intentionally *not* a derived value here.)
 *
 * `validate` returns soft [RuleWarning]s and never blocks (FR-005).
 */
class DnD35RuleSetTest {
    private val ruleSet = DnD35RuleSet(jacksonObjectMapper())

    @Test
    fun `id and definition load from the bundled JSON`() {
        assertEquals("dnd35", ruleSet.id())
        assertEquals("dnd35", ruleSet.definition().ruleSetId)
        assertTrue(ruleSet.definition().sections.isNotEmpty())
    }

    @Test
    fun `computes ability modifiers as floor of (score minus 10 over 2)`() {
        val out =
            ruleSet.computeDerived(
                mapOf(
                    "strength" to 18,
                    "dexterity" to 7,
                    "constitution" to 10,
                    "intelligence" to 13,
                    "wisdom" to 8,
                    "charisma" to 20,
                ),
            )
        assertEquals(4, out["strMod"]) // (18-10)/2
        assertEquals(-2, out["dexMod"]) // floor((7-10)/2) = floor(-1.5) = -2
        assertEquals(0, out["conMod"])
        assertEquals(1, out["intMod"]) // floor(3/2)
        assertEquals(-1, out["wisMod"]) // floor(-2/2)
        assertEquals(5, out["chaMod"])
    }

    @Test
    fun `computes saves as base plus the governing ability modifier, and initiative as dex mod`() {
        val out =
            ruleSet.computeDerived(
                mapOf(
                    "constitution" to 14, // conMod +2
                    "dexterity" to 16, // dexMod +3
                    "wisdom" to 9, // wisMod -1
                    "fortBase" to 2,
                    "refBase" to 0,
                    "willBase" to 6,
                ),
            )
        assertEquals(4, out["fortitude"]) // 2 + 2
        assertEquals(3, out["reflex"]) // 0 + 3
        assertEquals(5, out["will"]) // 6 + (-1)
        assertEquals(3, out["initiative"]) // = dexMod
    }

    @Test
    fun `is pure - it does not mutate the input and preserves base fields`() {
        val input: SheetData = mapOf("strength" to 16, "name" to "Conan")
        val out = ruleSet.computeDerived(input)

        assertEquals(2, input.size) // input untouched
        assertEquals("Conan", out["name"]) // base fields preserved
        assertEquals(3, out["strMod"]) // derived added
    }

    @Test
    fun `validate flags an ability score below 1 against its field id`() {
        val warnings =
            ruleSet.validate(
                mapOf("strength" to 0, "dexterity" to 12),
                SheetChange(previous = emptyMap(), changedFields = setOf("strength")),
            )
        assertEquals(1, warnings.size)
        assertEquals("strength", warnings.first().field)
    }

    @Test
    fun `validate returns no warnings for a valid sheet`() {
        val warnings =
            ruleSet.validate(
                mapOf(
                    "strength" to 10,
                    "dexterity" to 10,
                    "constitution" to 10,
                    "intelligence" to 10,
                    "wisdom" to 10,
                    "charisma" to 10,
                    "level" to 1,
                ),
                SheetChange(previous = emptyMap(), changedFields = emptySet()),
            )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `validate never blocks - it returns a list even for an empty sheet`() {
        val warnings = ruleSet.validate(emptyMap(), SheetChange(previous = emptyMap(), changedFields = emptySet()))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `the skills section is a table seeded with the canonical skill list (T106)`() {
        val skills =
            ruleSet
                .definition()
                .sections
                .first { it.id == "skills" }
                .fields
                .first()
        assertEquals(FieldType.TABLE, skills.type)
        assertTrue((skills.presetRows?.size ?: 0) >= 30) // the standard 3.5 skill list
        assertTrue(skills.columns!!.any { it.id == "total" && it.type == FieldType.DERIVED })
    }

    @Test
    fun `computes a skill row total from ranks plus its key-ability modifier (T106)`() {
        val out =
            ruleSet.computeDerived(
                mapOf(
                    "strength" to 18, // strMod +4
                    "skills" to listOf(mapOf("skill" to "Climb", "keyAbility" to "strMod", "ranks" to 8, "misc" to 1)),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val skills = out["skills"] as List<Map<String, Any?>>
        assertEquals(13, skills[0]["total"]) // 8 + strMod 4 + 1
    }

    @Test
    fun `validate warns when skill ranks exceed the 3-5 maximum for the level (T106)`() {
        val warnings =
            ruleSet.validate(
                mapOf(
                    "level" to 1, // class-skill max = level + 3 = 4
                    "skills" to listOf(mapOf("skill" to "Climb", "keyAbility" to "strMod", "ranks" to 8, "classSkill" to true)),
                ),
                SheetChange(previous = emptyMap(), changedFields = setOf("skills")),
            )
        assertEquals(1, warnings.size)
        assertEquals("skill.ranks-exceed-max", warnings.first().code)
        assertEquals("skills", warnings.first().field)
    }

    @Test
    fun `validate does not warn for skill ranks within the maximum (T106)`() {
        val warnings =
            ruleSet.validate(
                mapOf(
                    "level" to 5, // class-skill max = 8
                    "skills" to listOf(mapOf("skill" to "Climb", "keyAbility" to "strMod", "ranks" to 8, "classSkill" to true)),
                ),
                SheetChange(previous = emptyMap(), changedFields = emptySet()),
            )
        assertTrue(warnings.isEmpty())
    }
}
