package no.rauboti.tome.characters.data

import org.springframework.data.annotation.TypeAlias

/**
 * Dark Souls **base inputs** — a stub variant (US5, T072–T075) giving the sealed [CharacterBaseData] a
 * second member so every `when` stays exhaustive; fleshed out later. `@TypeAlias("darksouls")` pins the
 * stored discriminator.
 */
@TypeAlias("darksouls")
data class DarkSoulsCharacterBaseData(
    val name: String = "",
) : CharacterBaseData {
    override val ruleSetId: String get() = "darksouls"
}
