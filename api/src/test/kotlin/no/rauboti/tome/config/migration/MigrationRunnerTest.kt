package no.rauboti.tome.config.migration

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * Verifies the Spring Data-native migration mechanism (T089/T091): [MigrationRunner] applies
 * [C001__CreateCharacters] once on boot — creating the `characters` `{ userId: 1 }` index and recording
 * it in `_migrations` —
 * and a second run is a ledger-guarded no-op.
 *
 * The runner fires on `ApplicationReadyEvent` during context startup, so by the time these tests run
 * the migration has already been applied against the shared Mongo container ([IntegrationTest]).
 */
class MigrationRunnerTest : IntegrationTest() {
    @Autowired private lateinit var mongo: MongoTemplate

    @Autowired private lateinit var runner: MigrationRunner

    private fun c001LedgerCount(): Long = mongo.count(Query(Criteria.where("_id").`is`("C001")), "_migrations")

    @Test
    fun `C001 is applied once on boot and recorded in the ledger`() {
        assertEquals(1L, c001LedgerCount())
    }

    @Test
    fun `the characters collection has the userId index`() {
        val hasUserIdIndex =
            mongo.indexOps("characters").indexInfo.any { info ->
                info.indexFields.any { it.key == "userId" }
            }
        assertTrue(hasUserIdIndex, "expected a { userId: 1 } index on the characters collection")
    }

    @Test
    fun `re-running the migrations is a ledger-guarded no-op`() {
        runner.applyPending()
        assertEquals(1L, c001LedgerCount(), "C001 must not be recorded twice")
    }
}
