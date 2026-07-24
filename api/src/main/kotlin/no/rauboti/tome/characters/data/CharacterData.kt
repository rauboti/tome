package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The enriched/served side of a character sheet (ADR-001): a sealed hierarchy wrapping a
 * [CharacterBaseData] with derived values filled in. Serialized in REST responses, never persisted
 * (built on read by [enrich]). The wire discriminator (`@JsonTypeInfo` on `ruleSetId`) matches the
 * openapi response `Sheet` `oneOf`.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "ruleSetId",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DnD35CharacterData::class, name = "dnd35"),
    JsonSubTypes.Type(value = DarkSoulsCharacterData::class, name = "darksouls"),
)
sealed interface CharacterData {
    val ruleSetId: String
}

/**
 * Enrich stored base inputs into the served sheet. The `when` is exhaustive over the sealed hierarchy,
 * so adding a rule set is a compile error until handled here (ADR-001).
 */
fun CharacterBaseData.enrich(): CharacterData =
    when (this) {
        is DnD35CharacterBaseData -> DnD35CharacterData(this)
        is DarkSoulsCharacterBaseData -> DarkSoulsCharacterData(this)
    }
