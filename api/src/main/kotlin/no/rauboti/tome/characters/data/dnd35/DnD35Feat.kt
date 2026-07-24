package no.rauboti.tome.characters.data.dnd35

/** A feat row — no derived, so shared by base and enriched. */
data class DnD35Feat(
    val name: String = "",
    val type: String = "",
    val description: String = "",
)
