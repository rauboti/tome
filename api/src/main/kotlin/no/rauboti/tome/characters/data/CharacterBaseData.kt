package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The stored/request side of a character sheet (ADR-001): a sealed hierarchy of base inputs only,
 * enriched to the served [CharacterData] via [CharacterBaseData.enrich]. Two deliberately separate
 * discriminators on `ruleSetId`: Jackson `@JsonTypeInfo`/`@JsonSubTypes` for the wire, Spring Data's
 * `_class` (pinned per variant by `@TypeAlias`) for storage.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "ruleSetId",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DnD35CharacterBaseData::class, name = "dnd35"),
    JsonSubTypes.Type(value = DarkSoulsCharacterBaseData::class, name = "darksouls"),
)
sealed interface CharacterBaseData {
    val ruleSetId: String
}
