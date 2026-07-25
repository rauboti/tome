package no.rauboti.tome.config.migration

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * Verifies C002 (T038): on boot [MigrationRunner] creates the `campaigns` collection and its plain
 * lookup indexes — `{ dmId: 1 }`, `{ "members.playerId": 1 }`, `{ "members.characterId": 1 }` — and
 * records C002 in the `_migrations` ledger. The character index is deliberately **non-unique**: a
 * character may be in several campaigns at once (D6, amended 2026-07-25). Migrations fire on
 * `ApplicationReadyEvent`, so they are already applied here ([IntegrationTest]).
 */
class CampaignsMigrationTest : IntegrationTest() {
    @Autowired private lateinit var mongo: MongoTemplate

    /** The `campaigns` index whose keys are exactly [keys], or null if none matches. */
    private fun indexOn(vararg keys: String) =
        mongo.indexOps("campaigns").indexInfo.firstOrNull { info ->
            info.indexFields.map { it.key } == keys.toList()
        }

    @Test
    fun `C002 is applied once and recorded in the ledger`() {
        assertEquals(1L, mongo.count(Query(Criteria.where("_id").`is`("C002")), "_migrations"))
    }

    @Test
    fun `campaigns has the three roster lookup indexes`() {
        assertNotNull(indexOn("dmId"), "expected a { dmId: 1 } index on campaigns")
        assertNotNull(indexOn("members.playerId"), "expected a { \"members.playerId\": 1 } index on campaigns")
        assertNotNull(indexOn("members.characterId"), "expected a { \"members.characterId\": 1 } index on campaigns")
    }

    @Test
    fun `members-characterId is non-unique (a character may be in several campaigns)`() {
        val index = indexOn("members.characterId")
        assertNotNull(index)
        assertFalse(index!!.isUnique, "the members.characterId index must be a non-unique lookup")
    }
}
