package no.rauboti.tome.characters.data.dnd35

/** The six ability scores (base inputs). Modifiers are derived on the enriched [DnD35AbilityScores]. */
data class DnD35BaseAbilityScores(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
)

/** Ability scores plus their derived modifiers (`floor((score - 10) / 2)`). */
data class DnD35AbilityScores(
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int,
    val strMod: Int,
    val dexMod: Int,
    val conMod: Int,
    val intMod: Int,
    val wisMod: Int,
    val chaMod: Int,
) {
    /** Resolve an ability-mod field id (e.g. `"strMod"`) to its value; unknown → 0. */
    fun modOf(ref: String): Int =
        when (ref) {
            "strMod" -> strMod
            "dexMod" -> dexMod
            "conMod" -> conMod
            "intMod" -> intMod
            "wisMod" -> wisMod
            "chaMod" -> chaMod
            else -> 0
        }

    companion object {
        private fun mod(score: Int): Int = Math.floorDiv(score - 10, 2)

        fun from(s: DnD35BaseAbilityScores): DnD35AbilityScores =
            DnD35AbilityScores(
                s.strength,
                s.dexterity,
                s.constitution,
                s.intelligence,
                s.wisdom,
                s.charisma,
                mod(s.strength),
                mod(s.dexterity),
                mod(s.constitution),
                mod(s.intelligence),
                mod(s.wisdom),
                mod(s.charisma),
            )
    }
}
