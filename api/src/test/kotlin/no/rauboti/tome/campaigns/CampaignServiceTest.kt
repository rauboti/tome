package no.rauboti.tome.campaigns

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.rauboti.tome.campaigns.domain.Campaign
import no.rauboti.tome.campaigns.domain.CampaignMember
import no.rauboti.tome.characters.CharacterRepository
import no.rauboti.tome.characters.data.DnD35CharacterBaseData
import no.rauboti.tome.characters.domain.Character
import no.rauboti.tome.common.exceptions.BadRequestException
import no.rauboti.tome.common.exceptions.ConflictException
import no.rauboti.tome.common.exceptions.ForbiddenException
import no.rauboti.tome.common.exceptions.NotFoundException
import no.rauboti.tome.common.exceptions.StaleVersionException
import no.rauboti.tome.rulesets.DnD35RuleSet
import no.rauboti.tome.rulesets.RuleSetRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Instant
import java.util.UUID

/**
 * The campaign and roster rules of [CampaignService] (T041 — FR-008/FR-009/FR-010/FR-017, SC-003).
 *
 * Collaborators are MockK doubles so every rule and refusal path is exercised without a database — the
 * real `$push`/`$pull` persistence is [CampaignRepositoryTest]'s job, and the wired HTTP path is
 * T035/T036's. [PermissionService] is used **for real**, not mocked: role resolution is pure and it is
 * the very thing the DM-only rules must agree with.
 */
class CampaignServiceTest {
    private val campaigns = mockk<CampaignRepository>()
    private val characters = mockk<CharacterRepository>()
    private val ruleSets = RuleSetRegistry(listOf(DnD35RuleSet()))
    private val service = CampaignService(campaigns, characters, ruleSets, PermissionService())

    private val dm = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val aliceCharacter = UUID.randomUUID()

    private fun campaign(
        status: String = CampaignStatus.ACTIVE,
        ruleSetId: String = "dnd35",
        members: List<CampaignMember> = emptyList(),
        version: Int = 0,
    ): Campaign {
        val now = Instant.now()
        return Campaign(
            id = campaignId,
            dmId = dm,
            ruleSetId = ruleSetId,
            name = "Curse of Strahd",
            status = status,
            members = members,
            version = version,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun character(
        id: UUID = aliceCharacter,
        ownerId: UUID = alice,
        ruleSetId: String = "dnd35",
    ): Character {
        val now = Instant.now()
        return Character(
            id = id,
            userId = ownerId,
            ruleSetId = ruleSetId,
            name = "Ireena",
            data = DnD35CharacterBaseData(),
            version = 0,
            createdAt = now,
            updatedAt = now,
        )
    }

    // ---- create ---------------------------------------------------------------------------------

    @Test
    fun `create stores an active campaign owned by the caller, with an empty roster`() {
        val inserted = slot<Campaign>()
        every { campaigns.insert(capture(inserted)) } answers { inserted.captured }

        val created = service.create(dm, "Curse of Strahd", "dnd35")

        assertEquals(dm, created.dmId)
        assertEquals("dnd35", created.ruleSetId)
        assertEquals(CampaignStatus.ACTIVE, created.status)
        assertTrue(created.members.isEmpty())
        // @Version must start null so Spring Data treats the write as an insert.
        assertEquals(null, created.version)
    }

    @Test
    fun `create refuses an unknown rule set as a bad request, and stores nothing`() {
        val failure = assertThrows<BadRequestException> { service.create(dm, "Homebrew", "pathfinder") }

        assertTrue(failure.message!!.contains("pathfinder"))
        verify(exactly = 0) { campaigns.insert(any()) }
    }

    // ---- archive --------------------------------------------------------------------------------

    @Test
    fun `archive flips status to archived and keeps the roster intact (FR-010)`() {
        val member = CampaignMember(aliceCharacter, alice, Instant.now())
        every { campaigns.findById(campaignId) } returns campaign(members = listOf(member))
        val saved = slot<Campaign>()
        every { campaigns.save(capture(saved)) } answers { saved.captured }

        val archived = service.archive(campaignId, dm, expectedVersion = 0)

        assertEquals(CampaignStatus.ARCHIVED, archived.status)
        // FR-010: archiving preserves each member's character — the roster is not cleared.
        assertEquals(listOf(member), archived.members)
        assertEquals(0, saved.captured.version, "the caller's version must ride along for @Version")
    }

    @Test
    fun `archiving is terminal - an already-archived campaign is a conflict`() {
        every { campaigns.findById(campaignId) } returns campaign(status = CampaignStatus.ARCHIVED)

        val failure = assertThrows<ConflictException> { service.archive(campaignId, dm, expectedVersion = 0) }

        assertTrue(failure.message!!.contains("terminal"))
        verify(exactly = 0) { campaigns.save(any()) }
    }

    @Test
    fun `a stale version on archive is a 409, not an overwrite (SC-006)`() {
        every { campaigns.findById(campaignId) } returns campaign(version = 7)
        every { campaigns.save(any()) } throws OptimisticLockingFailureException("stale")

        assertThrows<StaleVersionException> { service.archive(campaignId, dm, expectedVersion = 3) }
    }

    @Test
    fun `only the DM may archive`() {
        every { campaigns.findById(campaignId) } returns campaign()

        assertThrows<ForbiddenException> { service.archive(campaignId, alice, expectedVersion = 0) }
        verify(exactly = 0) { campaigns.save(any()) }
    }

    @Test
    fun `archiving a campaign that does not exist is a 404`() {
        every { campaigns.findById(campaignId) } returns null

        assertThrows<NotFoundException> { service.archive(campaignId, dm, expectedVersion = 0) }
    }

    // ---- roster add -----------------------------------------------------------------------------

    @Test
    fun `addMember pushes a member recording the character's owner as playerId`() {
        every { campaigns.findById(campaignId) } returns campaign()
        every { characters.findById(aliceCharacter) } returns character()
        every { campaigns.addMember(campaignId, any()) } returns campaign()

        val member = service.addMember(campaignId, aliceCharacter, dm)

        assertEquals(aliceCharacter, member.characterId)
        // playerId is the character's owner, denormalized for authorization — not the acting DM.
        assertEquals(alice, member.playerId)
        verify { campaigns.addMember(campaignId, member) }
    }

    @Test
    fun `addMember refuses a rule-set mismatch with a reason naming both sets (FR-008, SC-003)`() {
        every { campaigns.findById(campaignId) } returns campaign(ruleSetId = "dnd35")
        every { characters.findById(aliceCharacter) } returns character(ruleSetId = "darksouls")

        val failure = assertThrows<ConflictException> { service.addMember(campaignId, aliceCharacter, dm) }

        assertTrue(failure.message!!.contains("darksouls"), "should name the character's rule set")
        assertTrue(failure.message!!.contains("dnd35"), "should name the campaign's rule set")
        verify(exactly = 0) { campaigns.addMember(any(), any()) }
    }

    @Test
    fun `addMember refuses a character already on this roster`() {
        every { campaigns.findById(campaignId) } returns
            campaign(members = listOf(CampaignMember(aliceCharacter, alice, Instant.now())))
        every { characters.findById(aliceCharacter) } returns character()

        val failure = assertThrows<ConflictException> { service.addMember(campaignId, aliceCharacter, dm) }

        assertTrue(failure.message!!.contains("already on the roster"))
        verify(exactly = 0) { campaigns.addMember(any(), any()) }
    }

    @Test
    fun `addMember does not care about other campaigns - a character may be in several (D6 amended)`() {
        // The roster of *this* campaign is empty; the character being busy elsewhere is irrelevant, so
        // the service must never consult a cross-campaign lookup.
        every { campaigns.findById(campaignId) } returns campaign()
        every { characters.findById(aliceCharacter) } returns character()
        every { campaigns.addMember(campaignId, any()) } returns campaign()

        service.addMember(campaignId, aliceCharacter, dm)

        verify(exactly = 0) { campaigns.findByMemberCharacterId(any()) }
    }

    @Test
    fun `a DM may add a character they own themselves, as an ordinary membership (FR-017)`() {
        val dmCharacter = UUID.randomUUID()
        every { campaigns.findById(campaignId) } returns campaign()
        every { characters.findById(dmCharacter) } returns character(id = dmCharacter, ownerId = dm)
        every { campaigns.addMember(campaignId, any()) } returns campaign()

        val member = service.addMember(campaignId, dmCharacter, dm)

        // An ordinary entry — owned by the DM as a player, with nothing marking it special.
        assertEquals(dm, member.playerId)
        assertEquals(dmCharacter, member.characterId)
    }

    @Test
    fun `addMember rejects an unknown character and a non-DM caller`() {
        every { campaigns.findById(campaignId) } returns campaign()
        every { characters.findById(aliceCharacter) } returns null

        assertThrows<NotFoundException> { service.addMember(campaignId, aliceCharacter, dm) }
        // A player may not add to the roster, even their own character.
        assertThrows<ForbiddenException> { service.addMember(campaignId, aliceCharacter, alice) }
        verify(exactly = 0) { campaigns.addMember(any(), any()) }
    }

    @Test
    fun `an archived campaign's roster is frozen`() {
        every { campaigns.findById(campaignId) } returns
            campaign(status = CampaignStatus.ARCHIVED, members = listOf(CampaignMember(aliceCharacter, alice, Instant.now())))

        assertThrows<ConflictException> { service.addMember(campaignId, aliceCharacter, dm) }
        assertThrows<ConflictException> { service.removeMember(campaignId, aliceCharacter, dm) }
        verify(exactly = 0) { campaigns.addMember(any(), any()) }
        verify(exactly = 0) { campaigns.removeMember(any(), any()) }
    }

    // ---- roster remove --------------------------------------------------------------------------

    @Test
    fun `removeMember pulls the membership and never touches the character (FR-009)`() {
        every { campaigns.findById(campaignId) } returns
            campaign(members = listOf(CampaignMember(aliceCharacter, alice, Instant.now())))
        every { campaigns.removeMember(campaignId, aliceCharacter) } returns campaign()

        service.removeMember(campaignId, aliceCharacter, dm)

        verify { campaigns.removeMember(campaignId, aliceCharacter) }
        // The character document is left alone — only the roster entry goes.
        verify(exactly = 0) { characters.deleteById(any()) }
    }

    @Test
    fun `removing a character that is not on the roster is a 404`() {
        every { campaigns.findById(campaignId) } returns campaign()

        assertThrows<NotFoundException> { service.removeMember(campaignId, aliceCharacter, dm) }
        verify(exactly = 0) { campaigns.removeMember(any(), any()) }
    }

    @Test
    fun `only the DM may remove a member`() {
        every { campaigns.findById(campaignId) } returns
            campaign(members = listOf(CampaignMember(aliceCharacter, alice, Instant.now())))

        assertThrows<ForbiddenException> { service.removeMember(campaignId, aliceCharacter, alice) }
        verify(exactly = 0) { campaigns.removeMember(any(), any()) }
    }
}
