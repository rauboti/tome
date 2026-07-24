package no.rauboti.tome.characters.data.dnd35

/** A gear row — no per-row derived (`totalWeight` is a sheet-level sum); shared by base and enriched. */
data class DnD35Gear(
    val item: String = "",
    val quantity: Int = 0,
    val weight: Int = 0,
    val notes: String = "",
)
