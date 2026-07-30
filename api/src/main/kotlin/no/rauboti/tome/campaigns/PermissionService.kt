package no.rauboti.tome.campaigns

import no.rauboti.tome.campaigns.domain.Campaign
import no.rauboti.tome.campaigns.domain.CampaignMember
import no.rauboti.tome.campaigns.domain.CampaignRole
import no.rauboti.tome.common.exceptions.ForbiddenException
import org.springframework.stereotype.Service
import java.util.UUID



/**
 * Campaign-scoped visibility decisions (T040 — FR-011/FR-012/FR-014, SC-004): the DM sees everything,
 * a player sees their own, everyone else is denied.
 *
 * Deliberately **pure**. Every decision is a function of an already-loaded [Campaign] aggregate plus
 * the caller's Hive subject — no repository, no I/O, no request or security context. Two things follow
 * from that, both of them the point rather than a side effect:
 *  - the *same* decision can be applied to a REST read and to an SSE fan-out, so a live update reaches
 *    only subscribers authorized for it (data-model §Authorization, SC-004);
 *  - the rules are exhaustively unit-testable without a database.
 *
 * Scope is the campaign and its roster. Private-vs-shared `npcs`/`content` predicates belong to US3,
 * arriving with the fields they read (FR-013/FR-016) — [Campaign] carries no such fields yet.
 */
@Service
class PermissionService {
    /**
     * The caller's [CampaignRole] in [campaign].
     *
     * The DM check comes **first** on purpose (FR-017): a DM who also runs a character of their own on
     * the roster is still the DM, never demoted to [CampaignRole.PLAYER] — so they cannot end up hiding
     * campaign content from themselves.
     */
    fun roleIn(
        campaign: Campaign,
        userId: UUID,
    ): CampaignRole =
        when {
            campaign.dmId == userId -> CampaignRole.DM
            campaign.members.any { it.playerId == userId } -> CampaignRole.PLAYER
            else -> CampaignRole.NONE
        }

    /** Whether [userId] may read [campaign] at all — the DM and any player on the roster may (FR-011/FR-012). */
    fun canViewCampaign(
        campaign: Campaign,
        userId: UUID,
    ): Boolean = roleIn(campaign, userId) != CampaignRole.NONE

    /**
     * Assert [userId] may read [campaign], else throw [ForbiddenException] (→ 403). The single deny
     * point for campaign reads, so FR-014's refusal reads the same wherever it is enforced.
     */
    fun requireCampaignAccess(
        campaign: Campaign,
        userId: UUID,
    ) {
        if (!canViewCampaign(campaign, userId)) {
            throw ForbiddenException("You do not have access to campaign '${campaign.id}'.")
        }
    }

    /**
     * Whether [userId] may read the sheet of [characterId] **by virtue of this campaign**: the DM may
     * read any character on the roster, a player only one they own, anyone else none (data-model
     * §Authorization, SC-004). A character that is not on this roster is always `false` — direct
     * owner access is US1's concern (`CharacterService`), not a campaign-scoped grant.
     *
     * Read-only by design: the DM's campaign-scoped access grants **reads, never writes**. Character
     * writes stay owner-only in `CharacterService`, which remains their single authority — this service
     * intentionally defines no campaign-scoped write rule to avoid a second source of truth.
     */
    fun canViewCharacter(
        campaign: Campaign,
        characterId: UUID,
        userId: UUID,
    ): Boolean {
        val member = campaign.members.firstOrNull { it.characterId == characterId } ?: return false
        return when (roleIn(campaign, userId)) {
            CampaignRole.DM -> true
            CampaignRole.PLAYER -> member.playerId == userId
            CampaignRole.NONE -> false
        }
    }

    /**
     * The roster slice [userId] may see: the DM gets every member (FR-012), a player only their own
     * entries (FR-011 "self"), a non-member nothing (FR-014).
     *
     * **Interpretation to confirm when the view is assembled (T042/T045):** data-model §Authorization
     * describes the player's roster as "self + shared", so this returns a player's own entries only —
     * the privacy-conservative reading, safe under SC-004. Whether a player should additionally see
     * *that* co-players are on the roster (their existence, not their sheets — which
     * [canViewCharacter] still withholds) is a product call this service does not presume to make.
     */
    fun visibleMembers(
        campaign: Campaign,
        userId: UUID,
    ): List<CampaignMember> =
        when (roleIn(campaign, userId)) {
            CampaignRole.DM -> campaign.members
            CampaignRole.PLAYER -> campaign.members.filter { it.playerId == userId }
            CampaignRole.NONE -> emptyList()
        }
}
