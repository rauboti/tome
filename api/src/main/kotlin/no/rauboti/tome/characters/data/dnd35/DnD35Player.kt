package no.rauboti.tome.characters.data.dnd35

/** The player behind the character: owner id (UUID) + display name, stamped at create and fixed. */
data class DnD35Player(
    val id: String = "",
    val name: String = "",
)
