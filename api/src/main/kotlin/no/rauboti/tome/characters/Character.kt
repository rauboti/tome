package no.rauboti.tome.characters

import no.rauboti.tome.characters.data.CharacterBaseData
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * A player character — the `characters` MongoDB document (US1, data-model.md §characters). [data] holds
 * the typed base inputs only ([CharacterBaseData], ADR-001); [name]/[ruleSetId]/[userId] are top-level
 * fields for roster queries, with `{ userId: 1 }` indexed (migration `C001`). [userId] is the owner's
 * Hive subject — no local user table (research D1). [version] backs Spring Data `@Version` optimistic
 * concurrency (research D5): `null` before persist, `0` on insert, incremented per save; a stale write
 * throws `OptimisticLockingFailureException` → `409` in the service (T096/T098).
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
