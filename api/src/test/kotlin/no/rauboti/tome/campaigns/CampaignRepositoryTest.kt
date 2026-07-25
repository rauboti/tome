package no.rauboti.tome.campaigns

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.util.UUID

/**
 * Persistence + roster behaviour of [Campaign] / [CampaignRepository] against a real MongoDB
 * ([IntegrationTest]): embedded-member round-trip, `@Version` optimistic concurrency, atomic
 * `$push`/`$pull` roster changes, and that a character may be in several campaigns at once (D6,
 * amended 2026-07-25) — surfaced via [CampaignRepository.findByMemberCharacterId].
 */
class CampaignRepositoryTest : IntegrationTest() {
    @Autowired private lateinit var repository: CampaignRepository

    @Autowired private lateinit var mongo: MongoTemplate

    @BeforeEach
    fun clearDocs() {
        // Clear documents but keep the C002 indexes — dropping the collection would drop them too.
        mongo.remove(Query(), "campaigns")
    }

    private fun campaign(
        id: UUID = UUID.randomUUID(),
        dmId: UUID = UUID.randomUUID(),
        status: String = CampaignStatus.ACTIVE,
        name: String = "Curse of Strahd",
        members: List<CampaignMember> = emptyList(),
    ): Campaign {
        val now = Instant.now()
        return Campaign(id, dmId, "dnd35", name, status, members, version = null, createdAt = now, updatedAt = now)
    }

    private fun member(
        characterId: UUID = UUID.randomUUID(),
        playerId: UUID = UUID.randomUUID(),
    ) = CampaignMember(characterId, playerId, Instant.now())

    @Test
    fun `insert then findById round-trips the campaign with its embedded members`() {
        val m = member()
        val saved = repository.insert(campaign(members = listOf(m)))
        assertEquals(0, saved.version, "@Version starts at 0 on insert")

        val found = repository.findById(saved.id)
        assertNotNull(found)
        assertEquals(saved.id, found!!.id)
        assertEquals("dnd35", found.ruleSetId)
        assertEquals(CampaignStatus.ACTIVE, found.status)
        assertEquals(1, found.members.size)
        assertEquals(m.characterId, found.members[0].characterId)
        assertEquals(m.playerId, found.members[0].playerId)
    }

    @Test
    fun `save bumps the version and a stale save is rejected`() {
        val saved = repository.insert(campaign())
        val updated = repository.save(saved.copy(name = "Renamed"))
        assertEquals(1, updated.version)

        // The original v0 instance is now stale — it must not overwrite the v1 document.
        assertThrows<OptimisticLockingFailureException> {
            repository.save(saved.copy(name = "Stale"))
        }
    }

    @Test
    fun `addMember pushes onto the roster and removeMember pulls just that entry`() {
        val created = repository.insert(campaign())
        val a = member()
        val b = member()

        assertNotNull(repository.addMember(created.id, a))
        val afterBoth = repository.addMember(created.id, b)
        assertEquals(2, afterBoth!!.members.size)

        val afterRemove = repository.removeMember(created.id, a.characterId)
        assertEquals(listOf(b.characterId), afterRemove!!.members.map { it.characterId })
    }

    @Test
    fun `findByDmId returns only that DM's campaigns`() {
        val dm = UUID.randomUUID()
        repository.insert(campaign(dmId = dm, name = "One"))
        repository.insert(campaign(dmId = dm, name = "Two"))
        repository.insert(campaign(dmId = UUID.randomUUID(), name = "Other DM"))

        val mine = repository.findByDmId(dm)
        assertEquals(2, mine.size)
        assertTrue(mine.all { it.dmId == dm })
    }

    @Test
    fun `a character can be in several campaigns at once and is found in each`() {
        val character = UUID.randomUUID()
        val main = repository.insert(campaign(name = "Long campaign"))
        val sideQuest = repository.insert(campaign(name = "Side quest"))

        // No cross-campaign uniqueness — the same character joins both active campaigns.
        assertNotNull(repository.addMember(main.id, member(characterId = character)))
        assertNotNull(repository.addMember(sideQuest.id, member(characterId = character)))

        val campaigns = repository.findByMemberCharacterId(character)
        assertEquals(setOf(main.id, sideQuest.id), campaigns.map { it.id }.toSet())
    }
}
