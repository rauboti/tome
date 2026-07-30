package no.rauboti.tome.characters.dto

import java.util.UUID

/** List/roster projection of a character (openapi `CharacterSummary`). */
data class CompactCharacterDto(
    val id: UUID,
    val name: String,
    val ruleSetId: String,
)
