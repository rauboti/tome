package no.rauboti.tome.characters

import no.rauboti.tome.rulesets.SheetData
import java.time.Instant
import java.util.UUID

/**
 * A player character — the persisted `characters` row (US1, data-model.md / migration `V1`). The
 * sheet values live in [data] (a [SheetData] map, stored as `jsonb`, shaped by the rule set's
 * definition and carrying the derived values the engine recomputes on every write); a few
 * cross-cutting values ([name]/[ruleSetId]/[userId]) are promoted to columns for lists and rosters.
 *
 * This is the raw storage model as read by [CharacterRepository]. The rule-set logic
 * (`RuleSet.computeDerived`/`validate`) and optimistic-concurrency orchestration live in the service
 * (T030); the REST projection (owner, HP, soft warnings) is assembled in the controller (T031).
 *
 * [userId] is the owner's Hive subject (there is no local user table — identity is Hive's, research
 * D1). [version] backs optimistic concurrency (research D5): a write must carry the version it read.
 */
data class Character(
    val id: UUID,
    val userId: UUID,
    val ruleSetId: String,
    val name: String,
    val data: SheetData,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
