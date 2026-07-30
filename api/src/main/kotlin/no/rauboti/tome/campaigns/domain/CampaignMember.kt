package no.rauboti.tome.campaigns.domain

import java.time.Instant
import java.util.UUID

/**
 * A roster entry (embedded in [Campaign.members]) linking a player's [characterId] into the campaign
 * (research D6). [playerId] is the character owner's Hive subject, denormalized for authorization.
 * Keyed by [characterId] within the campaign — no `_id` needed.
 */
data class CampaignMember(
    val characterId: UUID,
    val playerId: UUID,
    val addedAt: Instant,
)
