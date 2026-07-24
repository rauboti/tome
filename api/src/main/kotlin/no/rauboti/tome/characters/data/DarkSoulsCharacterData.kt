package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Dark Souls **enriched** sheet — a stub variant (US5, tasks T072–T075) wrapping
 * [DarkSoulsCharacterBaseData], so the sealed [CharacterData] hierarchy and `enrich` are exhaustive.
 * Gains real derived groups when the rule set is authored.
 */
data class DarkSoulsCharacterData(
    @get:JsonIgnore val base: DarkSoulsCharacterBaseData,
) : CharacterData {
    override val ruleSetId: String get() = base.ruleSetId

    val name: String get() = base.name
}
