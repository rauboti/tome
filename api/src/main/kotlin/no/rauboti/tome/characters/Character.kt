package no.rauboti.tome.characters

import no.rauboti.tome.characters.data.CharacterBaseData
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * A player character — the `characters` MongoDB document (US1, data-model.md §characters).
 *
 * [data] is the typed **base inputs only** ([CharacterBaseData], ADR-001; incl. entered HP); derived
 * values — ability modifiers, saves, AC, initiative, … — are computed on read by enriching to
 * `CharacterData` (`CharacterBaseData.enrich()`) and **never stored** (D8, by construction — the base
 * type has no derived properties). The cross-cutting values [name]/[ruleSetId]/[userId] are ordinary
 * top-level document fields for list/roster queries, with `{ userId: 1 }` indexed (migration `C001`).
 *
 * [userId] is the owner's Hive subject — identity is Hive's, there is no local user table (research D1).
 * [version] backs optimistic concurrency via Spring Data `@Version` (research D5): `null` on a
 * not-yet-persisted document, assigned `0` on insert and incremented on each save; a write carrying a
 * stale version fails with `OptimisticLockingFailureException` (mapped to `409` in the service, T096/T098).
 *
 * This is the storage model only — rule-set logic (`RuleSet.computeDerived`/`validate`), the
 * resolve-on-read projection, and concurrency orchestration live in the service/resolver (T094–T098).
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
