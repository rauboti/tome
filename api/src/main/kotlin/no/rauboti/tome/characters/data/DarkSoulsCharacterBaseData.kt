package no.rauboti.tome.characters.data

import org.springframework.data.annotation.TypeAlias

/**
 * Dark Souls **base inputs** — a stub variant (US5, tasks T072–T075) so the sealed [CharacterBaseData]
 * hierarchy has a second member and every rule-set-specific `when` is exhaustive. Fleshed out once its
 * lineage is resolved by a spec amendment. `@TypeAlias("darksouls")` pins the stored discriminator.
 */
@TypeAlias("darksouls")
data class DarkSoulsCharacterBaseData(
    val name: String = "",
) : CharacterBaseData {
    override val ruleSetId: String get() = "darksouls"
}
