package no.rauboti.tome.config.migration

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * Verifies C002 (T038): on boot [MigrationRunner] creates the `campaigns` collection and its indexes —
 * `{ dmId: 1 }`, `{ "members.playerId": 1 }`, and the **unique partial multikey**
 * `{ "members.characterId": 1 }` filtered to `status: "active"` (the "one active campaign per character"
 * invariant + no duplicate member, data-model §Invariants) — and records C002 in the `_migrations`
 * ledger. Migrations fire on `ApplicationReadyEvent`, so they are already applied here ([IntegrationTest]).
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
    fun `campaigns has the dmId and members-playerId lookup indexes`() {
        assertNotNull(indexOn("dmId"), "expected a { dmId: 1 } index on campaigns")
        assertNotNull(indexOn("members.playerId"), "expected a { \"members.playerId\": 1 } index on campaigns")
    }

    @Test
    fun `members-characterId is a unique partial index scoped to active campaigns`() {
        val index = indexOn("members.characterId")
        assertNotNull(index, "expected a { \"members.characterId\": 1 } index on campaigns")
        assertTrue(index!!.isUnique, "the members.characterId index must be unique (one active campaign per character)")
        val filter = index.partialFilterExpression
        assertNotNull(filter, "the members.characterId index must be partial (status: active)")
        assertTrue(
            filter!!.contains("status") && filter.contains("active"),
            "partial filter should be { status: \"active\" }, was: $filter",
        )
    }
}
