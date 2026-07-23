package no.rauboti.tome.characters

import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.RuleWarning
import no.rauboti.tome.rulesets.SheetChange
import no.rauboti.tome.rulesets.SheetData
import no.rauboti.tome.rulesets.SheetDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure unit test for [CharacterDataResolver] (no container). Uses a stub [RuleSet] so it verifies the
 * *resolver's* contract — base inputs preserved, derived values added, and recomputed-not-stored (D8) —
 * independently of any concrete rule set's formulas (those are covered by `DnD35RuleSetTest`).
 */
class CharacterDataResolverTest {
    private val resolver = CharacterDataResolver()

    /** Stub rule set: the sole "derived" value `doubled` = base input `n` × 2 (0 if absent). */
    private val stubRuleSet =
        object : RuleSet {
            override fun id(): String = "stub"

            override fun name(): String = "Stub"

            override fun definition(): SheetDefinition = error("definition() is not exercised by the resolver")

            override fun computeDerived(data: SheetData): SheetData = data + mapOf("doubled" to ((data["n"] as? Number)?.toInt() ?: 0) * 2)

            override fun validate(
                data: SheetData,
                change: SheetChange,
            ): List<RuleWarning> = emptyList()
        }

    @Test
    fun `resolve preserves base inputs and adds derived values`() {
        val resolved = resolver.resolve(mapOf("n" to 3, "note" to "keep"), stubRuleSet)

        assertEquals(3, resolved["n"], "base input must be preserved")
        assertEquals("keep", resolved["note"], "untouched base input must be preserved")
        assertEquals(6, resolved["doubled"], "derived value must be added")
    }

    @Test
    fun `resolve recomputes derived from current inputs, overwriting any stale stored value`() {
        // D8: derived is never stored, but if a stale value leaks into `data`, resolve overwrites it.
        val resolved = resolver.resolve(mapOf("n" to 5, "doubled" to 999), stubRuleSet)

        assertEquals(10, resolved["doubled"], "derived must be recomputed (5×2), not the stale 999")
    }
}
