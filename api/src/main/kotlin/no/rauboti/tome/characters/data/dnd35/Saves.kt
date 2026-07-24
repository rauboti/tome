package no.rauboti.tome.characters.data.dnd35

/** Saving-throw base inputs; the totals are derived on the enriched [DnD35Saves]. */
data class DnD35BaseSaves(
    val fortBase: Int = 0,
    val refBase: Int = 0,
    val willBase: Int = 0,
)

/** Saving-throw bases plus the derived totals (base + governing ability mod). */
data class DnD35Saves(
    val fortBase: Int,
    val refBase: Int,
    val willBase: Int,
    val fortitude: Int,
    val reflex: Int,
    val will: Int,
) {
    companion object {
        fun from(
            s: DnD35BaseSaves,
            abilities: DnD35AbilityScores,
        ): DnD35Saves =
            DnD35Saves(
                s.fortBase,
                s.refBase,
                s.willBase,
                fortitude = s.fortBase + abilities.conMod,
                reflex = s.refBase + abilities.dexMod,
                will = s.willBase + abilities.wisMod,
            )
    }
}
