package no.rauboti.tome.characters.dto

import no.rauboti.tome.characters.data.CharacterBaseData

/**
 * Update a character sheet. `data` (full base sheet) and `version` are required; `name` is optional
 * (null keeps the current). `version` carries optimistic concurrency (stale → 409); `data.ruleSetId`
 * must match the character's or the service answers 400.
 */
data class UpdateCharacterDto(
    val name: String? = null,
    val data: CharacterBaseData,
    val version: Int,
)
