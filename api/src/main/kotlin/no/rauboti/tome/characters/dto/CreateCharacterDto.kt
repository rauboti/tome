package no.rauboti.tome.characters.dto

import no.rauboti.tome.characters.data.CharacterBaseData

/**
 * Create a character. `name` and the typed base `data` are non-null, so a body missing either fails
 * deserialization → 400; `data.ruleSetId` selects the rule set (unknown → 400). A partial sheet is
 * fine — every base field defaults.
 */
data class CreateCharacterDto(
    val name: String,
    val data: CharacterBaseData,
)
