package no.rauboti.tome.characters.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * T122 (TDD). Pins the **wire** contract of the split (the model-level slice of T119, no HTTP wiring):
 *  - **requests** parse into the polymorphic [CharacterBaseData] (keyed on `ruleSetId`), unknown ids
 *    rejected;
 *  - **responses** serialize the enriched [CharacterData] with the discriminator and the derived values.
 *
 * Storage polymorphism is a separate mechanism (see [CharacterDataPersistenceTest]); the HTTP round-trip
 * + 409 stay in T119/T124.
 */
class CharacterDataSerdeTest {
    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    @Test
    fun `a request body deserializes into the concrete base variant by discriminator`() {
        val base: CharacterBaseData = mapper.readValue("""{"ruleSetId":"dnd35","abilities":{"strength":16},"level":3}""")
        val dnd = assertInstanceOf(DnD35CharacterBaseData::class.java, base)
        assertEquals(16, dnd.abilities.strength)
        assertEquals(3, dnd.level)
    }

    @Test
    fun `the darksouls discriminator yields the stub base variant`() {
        assertInstanceOf(
            DarkSoulsCharacterBaseData::class.java,
            mapper.readValue<CharacterBaseData>("""{"ruleSetId":"darksouls"}"""),
        )
    }

    @Test
    fun `an unknown ruleSetId is rejected`() {
        assertThrows(JacksonException::class.java) {
            mapper.readValue<CharacterBaseData>("""{"ruleSetId":"pathfinder","abilities":{"strength":16}}""")
        }
    }

    @Test
    fun `base inputs survive a serialize-deserialize round-trip`() {
        val original: CharacterBaseData =
            DnD35CharacterBaseData(
                abilities = DnD35AbilityScores(strength = 15, dexterity = 12),
                level = 4,
                baseAttackBonus = 3,
            )
        val roundTripped: CharacterBaseData = mapper.readValue(mapper.writeValueAsString(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun `the enriched response carries the discriminator and the computed derived`() {
        val json = mapper.writeValueAsString(DnD35CharacterBaseData(abilities = DnD35AbilityScores(strength = 16)).enrich())
        assertTrue(json.contains("\"ruleSetId\":\"dnd35\""), "discriminator on the wire: $json")
        assertTrue(json.contains("\"strMod\":3"), "readOnly derived (nested under abilities) present: $json")
    }
}
