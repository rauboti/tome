package no.rauboti.tome.characters.dto

import no.rauboti.tome.characters.data.CharacterData
import no.rauboti.tome.rulesets.domain.RuleWarning
import java.util.UUID

/**
 * Full character projection (openapi `Character`): the enriched sheet `data` ([CharacterData] — base
 * inputs plus derived), the soft `warnings` from the last validate, and the `version` to send on the
 * next write. HP lives inside `data` (the DnD35 `hitPoints` group), not as a top-level field in v1.
 */
data class CharacterDto(
    val id: UUID,
    val name: String,
    val ruleSetId: String,
    val ownerId: UUID,
    val data: CharacterData,
    val warnings: List<RuleWarning>,
    val version: Int,
)
