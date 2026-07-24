package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Dark Souls **enriched** sheet — a stub variant (US5, T072–T075) wrapping [DarkSoulsCharacterBaseData]
 * so [CharacterData] and `enrich` stay exhaustive; gains real derived groups later.
 */
data class DarkSoulsCharacterData(
    @get:JsonIgnore val base: DarkSoulsCharacterBaseData,
) : CharacterData {
    override val ruleSetId: String get() = base.ruleSetId

    val name: String get() = base.name
}
