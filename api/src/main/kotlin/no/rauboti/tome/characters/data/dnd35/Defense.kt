package no.rauboti.tome.characters.data.dnd35

/** Defense base inputs; the AC totals are derived on the enriched [DnD35Defense]. */
data class DnD35BaseDefense(
    val armorBonus: Int = 0,
    val shieldBonus: Int = 0,
    val naturalArmor: Int = 0,
    val deflection: Int = 0,
    val dodge: Int = 0,
    val sizeMod: Int = 0,
)

/** Defense bonuses plus the derived AC totals. */
data class DnD35Defense(
    val armorBonus: Int,
    val shieldBonus: Int,
    val naturalArmor: Int,
    val deflection: Int,
    val dodge: Int,
    val sizeMod: Int,
    val armorClass: Int,
    val touchAC: Int,
    val flatFootedAC: Int,
) {
    companion object {
        fun from(
            d: DnD35BaseDefense,
            abilities: DnD35AbilityScores,
        ): DnD35Defense =
            DnD35Defense(
                d.armorBonus,
                d.shieldBonus,
                d.naturalArmor,
                d.deflection,
                d.dodge,
                d.sizeMod,
                armorClass =
                    10 + d.armorBonus + d.shieldBonus + abilities.dexMod + d.sizeMod +
                        d.naturalArmor + d.deflection + d.dodge,
                touchAC = 10 + abilities.dexMod + d.sizeMod + d.deflection + d.dodge,
                flatFootedAC = 10 + d.armorBonus + d.shieldBonus + d.sizeMod + d.naturalArmor + d.deflection,
            )
    }
}
