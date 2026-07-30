package no.rauboti.tome.campaigns

import no.rauboti.tome.campaigns.domain.Campaign
import no.rauboti.tome.campaigns.domain.CampaignMember
import no.rauboti.tome.campaigns.domain.CampaignRole
import no.rauboti.tome.common.exceptions.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * The campaign visibility rules of [PermissionService] (T040 — FR-011/FR-012/FR-014/FR-017, SC-004):
 * DM full, player self, everyone else denied. Pure — no Spring context and no MongoDB, because every
 * decision is a function of a loaded [Campaign] plus the caller's subject.
 */
class PermissionServiceTest {
    private val service = PermissionService()

    private val dm = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val stranger = UUID.randomUUID()

    private val aliceCharacter = UUID.randomUUID()
    private val bobCharacter = UUID.randomUUID()

    private fun member(
        characterId: UUID,
        playerId: UUID,
    ) = CampaignMember(characterId, playerId, Instant.now())

    private fun campaign(
        dmId: UUID = dm,
        members: List<CampaignMember> = listOf(member(aliceCharacter, alice), member(bobCharacter, bob)),
    ): Campaign {
        val now = Instant.now()
        return Campaign(
            id = UUID.randomUUID(),
            dmId = dmId,
            ruleSetId = "dnd35",
            name = "Curse of Strahd",
            status = CampaignStatus.ACTIVE,
            members = members,
            version = 0,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `resolves the caller's role from the DM id and the roster`() {
        val campaign = campaign()

        assertEquals(CampaignRole.DM, service.roleIn(campaign, dm))
        assertEquals(CampaignRole.PLAYER, service.roleIn(campaign, alice))
        assertEquals(CampaignRole.PLAYER, service.roleIn(campaign, bob))
        assertEquals(CampaignRole.NONE, service.roleIn(campaign, stranger))
    }

    @Test
    fun `the DM and every roster player may read the campaign, a non-member may not`() {
        val campaign = campaign()

        assertTrue(service.canViewCampaign(campaign, dm))
        assertTrue(service.canViewCampaign(campaign, alice))
        assertFalse(service.canViewCampaign(campaign, stranger))

        // The deny path carries the campaign id, and is a 403 (authenticated but not permitted).
        service.requireCampaignAccess(campaign, alice) // does not throw
        val denied = assertThrows<ForbiddenException> { service.requireCampaignAccess(campaign, stranger) }
        assertTrue(denied.message!!.contains(campaign.id.toString()), "deny message should name the campaign")
    }

    @Test
    fun `a player may read only their own character, never a co-player's (SC-004)`() {
        val campaign = campaign()

        assertTrue(service.canViewCharacter(campaign, aliceCharacter, alice))
        assertFalse(service.canViewCharacter(campaign, bobCharacter, alice), "alice must not see bob's sheet")
        assertFalse(service.canViewCharacter(campaign, aliceCharacter, bob), "bob must not see alice's sheet")
        assertFalse(service.canViewCharacter(campaign, aliceCharacter, stranger))
    }

    @Test
    fun `the DM may read every character on the roster`() {
        val campaign = campaign()

        assertTrue(service.canViewCharacter(campaign, aliceCharacter, dm))
        assertTrue(service.canViewCharacter(campaign, bobCharacter, dm))
    }

    @Test
    fun `a character that is not on this roster is never visible through this campaign`() {
        val campaign = campaign()
        val outsideCharacter = UUID.randomUUID()

        // Not even for the DM — campaign-scoped access cannot reach beyond the roster.
        assertFalse(service.canViewCharacter(campaign, outsideCharacter, dm))
        assertFalse(service.canViewCharacter(campaign, outsideCharacter, alice))
    }

    @Test
    fun `the visible roster is everything for the DM, own entries for a player, nothing for a non-member`() {
        val secondAliceCharacter = UUID.randomUUID()
        val campaign =
            campaign(
                members =
                    listOf(
                        member(aliceCharacter, alice),
                        member(secondAliceCharacter, alice),
                        member(bobCharacter, bob),
                    ),
            )

        assertEquals(3, service.visibleMembers(campaign, dm).size)
        // A player owning several characters sees all of their own — and only those.
        assertEquals(
            setOf(aliceCharacter, secondAliceCharacter),
            service.visibleMembers(campaign, alice).map { it.characterId }.toSet(),
        )
        assertEquals(listOf(bobCharacter), service.visibleMembers(campaign, bob).map { it.characterId })
        assertTrue(service.visibleMembers(campaign, stranger).isEmpty())
    }

    @Test
    fun `a DM running their own character stays the DM and keeps the full view (FR-017)`() {
        val dmCharacter = UUID.randomUUID()
        val campaign =
            campaign(members = listOf(member(aliceCharacter, alice), member(dmCharacter, dm)))

        // Owning a roster character must not demote the DM to a player…
        assertEquals(CampaignRole.DM, service.roleIn(campaign, dm))
        // …so the DM cannot end up hiding campaign content from themselves.
        assertEquals(2, service.visibleMembers(campaign, dm).size)
        assertTrue(service.canViewCharacter(campaign, aliceCharacter, dm))
        assertTrue(service.canViewCharacter(campaign, dmCharacter, dm))
    }

    @Test
    fun `a DM running their own character grants co-players no DM visibility (FR-017)`() {
        val dmCharacter = UUID.randomUUID()
        val campaign =
            campaign(members = listOf(member(aliceCharacter, alice), member(dmCharacter, dm)))

        assertEquals(CampaignRole.PLAYER, service.roleIn(campaign, alice))
        assertEquals(listOf(aliceCharacter), service.visibleMembers(campaign, alice).map { it.characterId })
        assertFalse(service.canViewCharacter(campaign, dmCharacter, alice), "alice must not see the DM's own PC")
    }
}
