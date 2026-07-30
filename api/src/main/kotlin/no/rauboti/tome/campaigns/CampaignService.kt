package no.rauboti.tome.campaigns

import no.rauboti.tome.campaigns.domain.Campaign
import no.rauboti.tome.campaigns.domain.CampaignMember
import no.rauboti.tome.campaigns.domain.CampaignRole
import no.rauboti.tome.characters.CharacterRepository
import no.rauboti.tome.common.exceptions.BadRequestException
import no.rauboti.tome.common.exceptions.ConflictException
import no.rauboti.tome.common.exceptions.ForbiddenException
import no.rauboti.tome.common.exceptions.NotFoundException
import no.rauboti.tome.common.exceptions.StaleVersionException
import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.RuleSetRegistry
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application logic for campaigns and their roster (T041 — FR-008/FR-009/FR-010/FR-017).
 *
 * Every write is the DM's: role is resolved through [PermissionService] rather than by comparing
 * `dmId` here, so there is one authority for "who is the DM" (and FR-017's DM-first rule holds
 * automatically). Cross-document rules that MongoDB cannot express are enforced here, per data-model
 * §Invariants:
 *  - **rule-set match on join** (FR-008) — `character.ruleSetId == campaign.ruleSetId`;
 *  - **no duplicate member within one campaign** — a `characterId` already on this roster is refused.
 *    Deliberately **not** a cross-campaign check: a character MAY be in several campaigns at once
 *    (D6 amended 2026-07-25), which is why `C002`'s `{"members.characterId": 1}` index is a plain
 *    lookup and not unique.
 *
 * Roster changes go through the repository's atomic `$push`/`$pull`, so adding one member does not
 * version-lock the whole aggregate; [archive] is a whole-document write and therefore carries the
 * caller's `version` for optimistic concurrency (a stale one → [StaleVersionException] → 409).
 */
@Service
class CampaignService(
    private val campaigns: CampaignRepository,
    private val characters: CharacterRepository,
    private val ruleSets: RuleSetRegistry,
    private val permissions: PermissionService,
) {
    /** Create an active campaign run by [dmId], bound for life to [ruleSetId] (unknown → 400). */
    fun create(
        dmId: UUID,
        name: String,
        ruleSetId: String,
    ): Campaign {
        resolveForWrite(ruleSetId)
        val now = Instant.now()
        return campaigns.insert(
            Campaign(
                id = UUID.randomUUID(),
                dmId = dmId,
                ruleSetId = ruleSetId,
                name = name,
                status = CampaignStatus.ACTIVE,
                members = emptyList(),
                version = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Archive [id] (FR-010) — members' characters are untouched, only the campaign's status changes.
     *
     * **Terminal**: there is deliberately no un-archive path (data-model §State transitions), and
     * archiving an already-archived campaign is a 409 rather than a silent no-op. A stale
     * [expectedVersion] is a 409 too, via [StaleVersionException].
     */
    fun archive(
        id: UUID,
        callerId: UUID,
        expectedVersion: Int,
    ): Campaign {
        val campaign = requireDm(id, callerId)
        if (campaign.status == CampaignStatus.ARCHIVED) {
            throw ConflictException("Campaign '$id' is already archived; archiving is terminal.")
        }
        val toSave =
            campaign.copy(
                status = CampaignStatus.ARCHIVED,
                // Expected version so @Version rejects a stale write (SC-006).
                version = expectedVersion,
                updatedAt = Instant.now(),
            )
        return try {
            campaigns.save(toSave)
        } catch (e: OptimisticLockingFailureException) {
            throw StaleVersionException()
        }
    }

    /**
     * Add [characterId] to [campaignId]'s roster on the DM's behalf (FR-009), returning the new entry.
     *
     * Refuses a rule-set mismatch (FR-008/SC-003) and a character already on this roster, both as 409s
     * naming the reason. The member records the character's owner as `playerId`, denormalized so
     * authorization needs no second read.
     *
     * A DM adding a character they own themselves is allowed and creates an **ordinary** membership
     * (FR-017): nothing here special-cases it, so the DM keeps their DM role (they cannot hide content
     * from themselves) and co-players gain nothing — see [PermissionService].
     */
    fun addMember(
        campaignId: UUID,
        characterId: UUID,
        callerId: UUID,
    ): CampaignMember {
        val campaign = requireRosterChangeable(campaignId, callerId)
        val character =
            characters.findById(characterId) ?: throw NotFoundException("Character '$characterId' not found.")
        if (character.ruleSetId != campaign.ruleSetId) {
            throw ConflictException(
                "Character '$characterId' uses rule set '${character.ruleSetId}', " +
                    "but campaign '$campaignId' runs '${campaign.ruleSetId}'.",
            )
        }
        if (campaign.members.any { it.characterId == characterId }) {
            throw ConflictException("Character '$characterId' is already on the roster of campaign '$campaignId'.")
        }
        val member = CampaignMember(characterId = characterId, playerId = character.userId, addedAt = Instant.now())
        campaigns.addMember(campaignId, member) ?: throw NotFoundException("Campaign '$campaignId' not found.")
        return member
    }

    /**
     * Drop [characterId] from [campaignId]'s roster (FR-009). The character document itself is
     * **kept** — only the membership goes. A character that is not on the roster is a 404.
     */
    fun removeMember(
        campaignId: UUID,
        characterId: UUID,
        callerId: UUID,
    ) {
        val campaign = requireRosterChangeable(campaignId, callerId)
        if (campaign.members.none { it.characterId == characterId }) {
            throw NotFoundException("Character '$characterId' is not on the roster of campaign '$campaignId'.")
        }
        campaigns.removeMember(campaignId, characterId)
    }

    /** Load [campaignId] and assert [callerId] is its DM, else 404 (absent) / 403 (not the DM). */
    private fun requireDm(
        campaignId: UUID,
        callerId: UUID,
    ): Campaign {
        val campaign =
            campaigns.findById(campaignId) ?: throw NotFoundException("Campaign '$campaignId' not found.")
        if (permissions.roleIn(campaign, callerId) != CampaignRole.DM) {
            throw ForbiddenException("Only the DM may modify campaign '$campaignId'.")
        }
        return campaign
    }

    /**
     * As [requireDm], and additionally that the campaign can still take roster changes.
     *
     * **Interpretation** (data-model calls archiving terminal but does not spell this out): an archived
     * campaign's roster is frozen, so adds and removes are both refused. Archiving preserves members'
     * characters (FR-010), which reads as keeping the roster as the historical record rather than
     * leaving it editable.
     */
    private fun requireRosterChangeable(
        campaignId: UUID,
        callerId: UUID,
    ): Campaign {
        val campaign = requireDm(campaignId, callerId)
        if (campaign.status == CampaignStatus.ARCHIVED) {
            throw ConflictException("Campaign '$campaignId' is archived; its roster can no longer change.")
        }
        return campaign
    }

    /** Resolve the rule set for a write; an unrecognized/unsupported one is a bad request (not 404). */
    private fun resolveForWrite(ruleSetId: String): RuleSet =
        ruleSets.all().firstOrNull { it.id() == ruleSetId }
            ?: throw BadRequestException("Unknown or unsupported rule set '$ruleSetId'.")
}
