package no.rauboti.tome.characters.data

import com.mongodb.client.model.Filters
import no.rauboti.tome.support.IntegrationTest
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument

/**
 * T122 (TDD). Pins the **storage** contract (the model-level slice of T119, no service wiring): the
 * stored [CharacterBaseData] round-trips through `MongoTemplate` to the correct concrete variant, the
 * discriminator is stored as `_class` = the `@TypeAlias` (not the class FQN), and — because the base
 * type has no derived properties at all — nothing derived can leak into storage. Enriching a reloaded
 * base recomputes the derived.
 *
 * Uses a tiny test-local wrapper document to exercise the sheet type before `Character.data` is retyped
 * to [CharacterBaseData] in T124.
 */
class CharacterDataPersistenceTest : IntegrationTest() {
    @Autowired private lateinit var mongo: MongoTemplate

    private fun storedData(id: UUID): Document {
        val doc = mongo.getCollection(COLLECTION).find(Filters.eq("_id", id)).first()
        assertNotNull(doc, "document '$id' should be persisted")
        return doc!!["data"] as Document
    }

    @Test
    fun `a DnD35 base sheet round-trips to the concrete variant with inputs intact`() {
        val id = UUID.randomUUID()
        mongo.save(
            SheetHolder(
                id,
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(strength = 16),
                    level = 3,
                    skills = listOf(DnD35SkillRowInput(skill = "Climb", keyAbility = "strMod", ranks = 5)),
                ),
            ),
        )

        val reloaded = mongo.findById(id, SheetHolder::class.java)!!.data
        val dnd = assertInstanceOf(DnD35CharacterBaseData::class.java, reloaded)
        assertEquals(16, dnd.abilities.strength)
        assertEquals(3, dnd.level)
        assertEquals("Climb", dnd.skills.single().skill)
        // Enriching the reloaded base recomputes the derived.
        assertEquals(3, DnD35CharacterData(dnd).abilities.strMod)
    }

    @Test
    fun `the stored discriminator is the TypeAlias, and no derived leaks into storage`() {
        val id = UUID.randomUUID()
        mongo.save(
            SheetHolder(
                id,
                DnD35CharacterBaseData(abilities = DnD35AbilityScores(strength = 18, dexterity = 14), baseAttackBonus = 6),
            ),
        )

        val data = storedData(id)
        assertEquals("dnd35", data.getString("_class"), "discriminator = @TypeAlias, not FQN")
        assertFalse(data.getString("_class").contains("no.rauboti"), "storage not coupled to the class FQN")

        val abilities = data.get("abilities", Document::class.java)
        assertNotNull(abilities, "the abilities group is stored")
        assertTrue(abilities!!.containsKey("strength"), "ability scores are stored")
        assertFalse(abilities.containsKey("strMod"), "no mods on the base type → none stored")

        // The enriched-only derived have no home on the base type; confirm none leaked to the document.
        for (derived in listOf("initiative", "grapple", "armorClass", "touchAC", "flatFootedAC", "totalWeight")) {
            assertFalse(data.containsKey(derived), "derived '$derived' must not be persisted")
        }
        assertFalse(data.containsKey("ruleSetId"), "the computed ruleSetId is not stored (it is the _class alias)")
    }

    @Test
    fun `the darksouls stub base variant round-trips under its own alias`() {
        val id = UUID.randomUUID()
        mongo.save(SheetHolder(id, DarkSoulsCharacterBaseData()))

        assertInstanceOf(DarkSoulsCharacterBaseData::class.java, mongo.findById(id, SheetHolder::class.java)!!.data)
        assertEquals("darksouls", storedData(id).getString("_class"))
    }

    @MongoDocument(collection = COLLECTION)
    data class SheetHolder(
        @Id val id: UUID,
        val data: CharacterBaseData,
    )

    private companion object {
        const val COLLECTION = "t122_character_data_roundtrip"
    }
}
