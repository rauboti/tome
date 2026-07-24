package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The **stored / request** side of a character sheet (ADR-001): a sealed hierarchy holding **base
 * inputs only** — no derived values. This is what Mongo persists, what POST/PUT bodies parse into, and
 * the single source every derived value is computed from. Enriching it (adding grouped derived) yields
 * the served [CharacterData]; see [CharacterBaseData.enrich].
 *
 * Discriminators, deliberately separate:
 *  - **wire (requests)** — Jackson `@JsonTypeInfo`/`@JsonSubTypes` on `ruleSetId` (openapi request `data`);
 *  - **storage** — Spring Data's `_class`, pinned to a stable string by each variant's `@TypeAlias`.
 *
 * Because this type has no derived properties at all, "derived is never stored" holds trivially — there
 * is nothing to strip.
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
