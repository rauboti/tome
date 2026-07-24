package no.rauboti.tome.characters.data.dnd35

import kotlin.math.max
import kotlin.math.min

/** A per-spell-level slot row's base inputs; bonus/total are derived on the enriched [DnD35SpellSlot]. */
data class DnD35BaseSpellSlot(
    val spellLevel: Int = 0,
    val slotsPerDay: Int = 0,
    val known: Int = 0,
    val prepared: Int = 0,
)

/** A per-spell-level slot row with derived bonus spells and total slots. */
data class DnD35SpellSlot(
    val spellLevel: Int,
    val slotsPerDay: Int,
    val known: Int,
    val prepared: Int,
    val bonusSpells: Int,
    val total: Int,
) {
    companion object {
        fun from(
            r: DnD35BaseSpellSlot,
            spellKeyAbility: String,
            abilities: DnD35AbilityScores,
        ): DnD35SpellSlot {
            val bonus = min(r.spellLevel, 1) * max(0, Math.floorDiv(abilities.modOf(spellKeyAbility) - r.spellLevel, 4) + 1)
            return DnD35SpellSlot(r.spellLevel, r.slotsPerDay, r.known, r.prepared, bonusSpells = bonus, total = r.slotsPerDay + bonus)
        }
    }
}
