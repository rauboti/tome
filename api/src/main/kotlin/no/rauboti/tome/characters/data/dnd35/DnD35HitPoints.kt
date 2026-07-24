package no.rauboti.tome.characters.data.dnd35

/** Entered hit points — HP is entered in 3.5, not derived; shared by base and enriched. */
data class DnD35HitPoints(
    val max: Int = 0,
    val current: Int = 0,
)
