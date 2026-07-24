package no.rauboti.tome.characters.data.dnd35

/** A weapon/attack row's base inputs; `attackBonus` is derived on the enriched [DnD35Attack]. */
data class DnD35BaseAttack(
    val weapon: String = "",
    val ability: String = "",
    val misc: Int = 0,
    val damage: String = "",
    val critical: String = "",
    val range: String = "",
    val notes: String = "",
)

/** A weapon/attack row with its derived attack bonus (Base Attack Bonus + ability mod + misc). */
data class DnD35Attack(
    val weapon: String,
    val ability: String,
    val misc: Int,
    val damage: String,
    val critical: String,
    val range: String,
    val notes: String,
    val attackBonus: Int,
) {
    companion object {
        fun from(
            r: DnD35BaseAttack,
            baseAttackBonus: Int,
            abilities: DnD35AbilityScores,
        ): DnD35Attack =
            DnD35Attack(
                r.weapon,
                r.ability,
                r.misc,
                r.damage,
                r.critical,
                r.range,
                r.notes,
                attackBonus = baseAttackBonus + abilities.modOf(r.ability) + r.misc,
            )
    }
}
