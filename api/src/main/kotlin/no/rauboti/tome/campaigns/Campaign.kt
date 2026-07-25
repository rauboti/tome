package no.rauboti.tome.campaigns

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * A campaign — the `campaigns` MongoDB document and US2 aggregate root (data-model §campaigns). A DM
 * ([dmId], the Hive subject) runs it under a fixed [ruleSetId] that gates which characters may join
 * (FR-008). The roster ([members]) is **embedded**, not a separate collection; a character may belong
 * to several campaigns at once (D6, amended 2026-07-25). [version] backs Spring Data `@Version`
 * optimistic concurrency (`null` before persist, `0` on insert; a stale save throws
 * `OptimisticLockingFailureException` → `409`).
 *
 * `npcs`/`content`/`rolls` (also embedded per the data model) arrive with their own US2/US3 tasks.
 */
@Document(collection = "campaigns")
data class Campaign(
    @Id val id: UUID,
    val dmId: UUID,
    val ruleSetId: String,
    val name: String,
    /** Lifecycle state — see [CampaignStatus]. Not indexed. */
    val status: String,
    val members: List<CampaignMember> = emptyList(),
    @Version val version: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

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

/**
 * The allowed [Campaign.status] values: `suspended` = paused without archiving. A plain string (not a
 * Kotlin enum) to sidestep Spring Data Mongo's uppercase enum serialization; `status` is not indexed,
 * so the value is only read/compared in the service.
 */
object CampaignStatus {
    const val ACTIVE = "active"
    const val ARCHIVED = "archived"
    const val SUSPENDED = "suspended"
}
