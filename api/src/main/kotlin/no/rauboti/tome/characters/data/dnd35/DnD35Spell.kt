package no.rauboti.tome.characters.data.dnd35

/** A known/prepared spell row — no derived; shared by base and enriched. */
data class DnD35Spell(
    val spell: String = "",
    val level: Int = 0,
    val prepared: Int = 0,
    val notes: String = "",
)
