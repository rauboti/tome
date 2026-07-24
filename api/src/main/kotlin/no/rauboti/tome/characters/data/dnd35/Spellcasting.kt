package no.rauboti.tome.characters.data.dnd35

/** Spellcasting base inputs; the save DC and per-slot totals are derived on the enriched [DnD35Spellcasting]. */
data class DnD35BaseSpellcasting(
    val casterClass: String = "",
    val casterLevel: Int = 0,
    val spellKeyAbility: String = "",
    val spellSlots: List<DnD35BaseSpellSlot> = emptyList(),
    val spells: List<DnD35Spell> = emptyList(),
)

/** Spellcasting inputs plus the derived save-DC base and per-slot totals. */
data class DnD35Spellcasting(
    val casterClass: String,
    val casterLevel: Int,
    val spellKeyAbility: String,
    val saveDcBase: Int,
    val spellSlots: List<DnD35SpellSlot>,
    val spells: List<DnD35Spell>,
) {
    companion object {
        fun from(
            s: DnD35BaseSpellcasting,
            abilities: DnD35AbilityScores,
        ): DnD35Spellcasting =
            DnD35Spellcasting(
                s.casterClass,
                s.casterLevel,
                s.spellKeyAbility,
                saveDcBase = 10 + abilities.modOf(s.spellKeyAbility),
                spellSlots = s.spellSlots.map { DnD35SpellSlot.from(it, s.spellKeyAbility, abilities) },
                spells = s.spells,
            )
    }
}
