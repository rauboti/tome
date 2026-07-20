package no.rauboti.tome.common

import no.rauboti.tome.rulesets.SheetData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Pure unit test for the JSONB conversion helper — no DB. Proves a sheet survives the
 * serialize → deserialize round-trip with nested objects, lists, and nulls intact, and that
 * empty/null column values decode to an empty sheet. The full DB round-trip through JdbcTemplate
 * (with the `cast(? as jsonb)` bind) is covered by the US1 integration test (T026).
 */
class JsonbSupportTest {
    private val support = JsonbSupport(jacksonObjectMapper())

    @Test
    fun `round-trips a nested sheet with ints, strings, bools, lists and nulls`() {
        val sheet: SheetData =
            mapOf(
                "name" to "Conan",
                "level" to 5,
                "alive" to true,
                "feats" to listOf("Cleave", "Power Attack"),
                "abilities" to mapOf("str" to 18, "dex" to 14),
                "notes" to null,
            )

        val restored = support.fromJson(support.toJson(sheet))
        assertEquals(sheet, restored)
    }

    @Test
    fun `null and blank column values decode to an empty sheet`() {
        assertEquals(emptyMap<String, Any?>(), support.fromJson(null))
        assertEquals(emptyMap<String, Any?>(), support.fromJson(""))
        assertEquals(emptyMap<String, Any?>(), support.fromJson("   "))
    }

    @Test
    fun `an explicit null field survives the round-trip as a present null`() {
        val restored = support.fromJson(support.toJson(mapOf("notes" to null)))
        assertTrue(restored.containsKey("notes"))
        assertNull(restored["notes"])
    }
}
