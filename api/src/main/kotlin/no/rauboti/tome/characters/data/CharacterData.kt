package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The **enriched / served** side of a character sheet (ADR-001): a sealed hierarchy that wraps a
 * [CharacterBaseData] and exposes its groups **with derived values filled in** (ability mods, saves,
 * AC, spell DC, per-row totals). This is what REST responses serialize; it is **never persisted** —
 * it is built on read from the stored base by [enrich], so derived can never drift and never need
 * stripping.
 *
 * The wire discriminator (`@JsonTypeInfo` on `ruleSetId`) matches the openapi response `Sheet` `oneOf`.
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
 * Enrich stored base inputs into the served sheet — the single map from the stored world to the read
 * world. The `when` is exhaustive over the sealed hierarchy, so adding a rule set is a compile error
 * until it is handled here (the ADR-001 "compiler enumerates the sites" property).
 */
fun CharacterBaseData.enrich(): CharacterData =
    when (this) {
        is DnD35CharacterBaseData -> DnD35CharacterData(this)
        is DarkSoulsCharacterBaseData -> DarkSoulsCharacterData(this)
    }
