package no.rauboti.tome.characters.domain

import no.rauboti.tome.characters.data.CharacterBaseData
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * A player character — the `characters` MongoDB document. [name]/[ruleSetId]/[userId] are top-level
 * fields for roster queries. [userId] is the owner's Hive subject — no local user table. [version]
 * backs Spring Data `@Version` optimistic concurrency: `null` before persist, `0` on insert, incremented
 * per save; a stale write throws `OptimisticLockingFailureException` → `409` in the service (T096/T098).
 */
@Document(collection = "characters")
data class Character(
    @Id val id: UUID,
    val userId: UUID,
    val ruleSetId: String,
    val name: String,
    val data: CharacterBaseData,
    @Version val version: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
