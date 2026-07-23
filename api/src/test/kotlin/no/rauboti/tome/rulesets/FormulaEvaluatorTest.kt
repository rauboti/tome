package no.rauboti.tome.rulesets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit test for the shared [FormulaEvaluator] (T105). Mirrors the web `derive.test.ts` cases (parity)
 * plus the two structured-sheet primitives `sum(table.column)` and `ref(field)`.
 */
class FormulaEvaluatorTest {
    private fun eval(
        expr: String,
        scope: Map<String, Any?> = emptyMap(),
    ) = FormulaEvaluator.evaluate(expr, scope)

    @Test
    fun `evaluates the 3-5 ability-modifier formula with floor and negatives`() {
        assertEquals(4.0, eval("floor((strength - 10) / 2)", mapOf("strength" to 18)))
        assertEquals(-2.0, eval("floor((strength - 10) / 2)", mapOf("strength" to 7))) // floor(-1.5)
    }

    @Test
    fun `adds identifiers and respects precedence and parentheses`() {
        assertEquals(3.0, eval("fortBase + conMod", mapOf("fortBase" to 2, "conMod" to 1)))
        assertEquals(14.0, eval("2 + 3 * 4"))
        assertEquals(20.0, eval("(2 + 3) * 4"))
    }

    @Test
    fun `an unknown identifier reads as 0`() {
        assertEquals(-5.0, eval("floor((strength - 10) / 2)"))
        assertEquals(0.0, eval("dexMod"))
    }

    @Test
    fun `malformed or unsafe input returns null`() {
        assertNull(eval("floor((strength - 10) / 2", mapOf("strength" to 10))) // unbalanced
        assertNull(eval("strength +", mapOf("strength" to 1)))
        assertNull(eval("1 % 2")) // unsupported operator
        assertNull(eval("nope(1)")) // unknown function
    }

    @Test
    fun `sum totals a numeric column across a table field's rows`() {
        val scope = mapOf("gear" to listOf(mapOf("weight" to 8), mapOf("weight" to 50), mapOf("weight" to 2)))
        assertEquals(60.0, eval("sum(gear.weight)", scope))
    }

    @Test
    fun `sum reads a missing table or column as 0`() {
        assertEquals(0.0, eval("sum(gear.weight)")) // no such table
        assertEquals(0.0, eval("sum(gear.weight)", mapOf("gear" to listOf(mapOf("item" to "x"))))) // no such column
    }

    @Test
    fun `ref resolves the value of the field named by another field`() {
        // A skill row's total: ranks + the modifier named by keyAbility ("strMod") + misc.
        val scope = mapOf("keyAbility" to "strMod", "strMod" to 4, "ranks" to 8, "misc" to 1)
        assertEquals(13.0, eval("ranks + ref(keyAbility) + misc", scope))
    }

    @Test
    fun `ref reads a missing or non-string reference as 0`() {
        assertEquals(0.0, eval("ref(keyAbility)")) // keyAbility absent
        assertEquals(0.0, eval("ref(keyAbility)", mapOf("keyAbility" to 5))) // not a field name
    }
}
