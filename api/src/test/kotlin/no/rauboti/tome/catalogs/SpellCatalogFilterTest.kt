package no.rauboti.tome.catalogs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Unit test for [SpellCatalog] (T113) — the class-filtered spell option source. Pure: constructs the
 * catalog with a bare Jackson mapper (it reads the bundled `spells.json` off the classpath).
 */
class SpellCatalogFilterTest {
    private val catalog = SpellCatalog(jacksonObjectMapper())

    @Test
    fun `identifies as the dnd35 spells catalog`() {
        assertEquals("dnd35", catalog.ruleSetId())
        assertEquals("spells", catalog.name())
    }

    @Test
    fun `filters spells to a caster class, with the per-class level in meta`() {
        val wizard = catalog.options("wizard")
        assertTrue(wizard.isNotEmpty())

        val fireball = wizard.first { it.value == "fireball" }
        assertEquals("Fireball", fireball.label)
        assertEquals(3, fireball.meta?.get("level"))

        // A divine-only spell is not on the wizard list.
        assertTrue(wizard.none { it.value == "cureLightWounds" }, "cure light wounds should not be a wizard spell")
    }

    @Test
    fun `a spell on multiple lists reports the level for the filtered class`() {
        assertEquals(
            1,
            catalog
                .options("cleric")
                .first { it.value == "cureLightWounds" }
                .meta
                ?.get("level"),
        )
        assertEquals(
            2,
            catalog
                .options("ranger")
                .first { it.value == "cureLightWounds" }
                .meta
                ?.get("level"),
        )
    }

    @Test
    fun `results are ordered by level then name`() {
        val wizard = catalog.options("wizard")
        val levels = wizard.mapNotNull { it.meta?.get("level") as? Int }
        assertEquals(levels.sorted(), levels, "options should be ordered by spell level")
    }

    @Test
    fun `a blank or absent class filter yields no options`() {
        assertTrue(catalog.options(null).isEmpty())
        assertTrue(catalog.options("").isEmpty())
        assertTrue(catalog.options("   ").isEmpty())
    }
}
