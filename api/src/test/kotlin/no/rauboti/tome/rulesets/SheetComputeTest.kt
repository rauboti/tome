package no.rauboti.tome.rulesets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit test for the rule-set-agnostic [SheetCompute] (T105) — the definition-driven compute-on-read
 * step. Exercises the new structured-sheet capabilities (per-row table derivation via `ref`, and a
 * sheet-level `sum` over a table column) plus derived-on-derived ordering, all against a synthetic
 * definition so it stays independent of the bundled dnd35 content.
 */
class SheetComputeTest {
    private val definition =
        SheetDefinition(
            ruleSetId = "test",
            version = "1.0.0",
            sections =
                listOf(
                    SheetSection(
                        id = "abilities",
                        labelKey = "s.abilities",
                        fields =
                            listOf(
                                SheetField("strength", "f.str", FieldType.INT),
                                SheetField("strMod", "f.strMod", FieldType.DERIVED, derivedFrom = "floor((strength - 10) / 2)"),
                            ),
                    ),
                    SheetSection(
                        id = "skills",
                        labelKey = "s.skills",
                        fields =
                            listOf(
                                SheetField(
                                    id = "skills",
                                    labelKey = "f.skills",
                                    type = FieldType.TABLE,
                                    columns =
                                        listOf(
                                            SheetField("skill", "c.skill", FieldType.TEXT),
                                            SheetField("keyAbility", "c.keyAbility", FieldType.TEXT),
                                            SheetField("ranks", "c.ranks", FieldType.INT),
                                            SheetField("misc", "c.misc", FieldType.INT),
                                            SheetField(
                                                "total",
                                                "c.total",
                                                FieldType.DERIVED,
                                                derivedFrom = "ranks + ref(keyAbility) + misc",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                    SheetSection(
                        id = "gear",
                        labelKey = "s.gear",
                        fields =
                            listOf(
                                SheetField(
                                    id = "gear",
                                    labelKey = "f.gear",
                                    type = FieldType.TABLE,
                                    columns =
                                        listOf(
                                            SheetField("item", "c.item", FieldType.TEXT),
                                            SheetField("weight", "c.weight", FieldType.INT),
                                        ),
                                ),
                                SheetField("totalWeight", "f.totalWeight", FieldType.DERIVED, derivedFrom = "sum(gear.weight)"),
                            ),
                    ),
                ),
        )

    @Test
    fun `resolves top-level derived, per-row table totals via ref, and a sheet-level sum`() {
        val data: SheetData =
            mapOf(
                "strength" to 18,
                "skills" to
                    listOf(
                        mapOf("skill" to "climb", "keyAbility" to "strMod", "ranks" to 8, "misc" to 0),
                        mapOf("skill" to "jump", "keyAbility" to "strMod", "ranks" to 5, "misc" to 2),
                    ),
                "gear" to listOf(mapOf("item" to "sword", "weight" to 8), mapOf("item" to "plate", "weight" to 50)),
            )

        val out = SheetCompute.resolve(definition, data)

        assertEquals(4, out["strMod"]) // floor((18-10)/2)
        assertEquals(58, out["totalWeight"]) // 8 + 50

        @Suppress("UNCHECKED_CAST")
        val skills = out["skills"] as List<Map<String, Any?>>
        assertEquals(12, skills[0]["total"]) // 8 + strMod 4 + 0
        assertEquals(11, skills[1]["total"]) // 5 + strMod 4 + 2
        // Base cells are preserved on the resolved rows.
        assertEquals("climb", skills[0]["skill"])
    }

    @Test
    fun `is pure - does not mutate the input sheet or its table rows`() {
        val row = mapOf("skill" to "climb", "keyAbility" to "strMod", "ranks" to 8, "misc" to 0)
        val data: SheetData = mapOf("strength" to 16, "skills" to listOf(row))

        SheetCompute.resolve(definition, data)

        assertEquals(2, data.size) // top-level input untouched
        assertEquals(4, row.size) // the row map was not given a `total` cell
    }
}
