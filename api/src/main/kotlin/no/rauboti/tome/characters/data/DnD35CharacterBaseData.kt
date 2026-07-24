package no.rauboti.tome.characters.data

import org.springframework.data.annotation.TypeAlias

/**
 * D&D 3.5 **base inputs** (ADR-001) — the stored/edited/parsed sheet. Identity fields stay top-level;
 * cohesive inputs are grouped ([abilities]/[hitPoints]/[defense]/[saves]/[spellcasting]). Holds **no
 * derived values**: those are added by enriching to [DnD35CharacterData] on read
 * ([CharacterBaseData.enrich]). Content mirrors the Phase 3C sheet (tasks T105–T114).
 *
 * `@TypeAlias("dnd35")` pins the stored `_class` discriminator (not the class FQN). All fields default,
 * so a partial sheet (create/edit) constructs cleanly.
 */
@TypeAlias("dnd35")
data class DnD35CharacterBaseData(
    // identity
    val name: String = "",
    val player: String = "",
    val race: String = "",
    val characterClass: String = "",
    val alignment: String = "",
    val deity: String = "",
    val size: String = "",
    val level: Int = 1,
    val experience: Int = 0,
    // grouped inputs
    val abilities: DnD35AbilityScores = DnD35AbilityScores(),
    val hitPoints: DnD35HitPoints = DnD35HitPoints(),
    val defense: DnD35DefenseInputs = DnD35DefenseInputs(),
    val saves: DnD35SaveInputs = DnD35SaveInputs(),
    // combat
    val baseAttackBonus: Int = 0,
    val grappleSizeMod: Int = 0,
    // tables
    val attacks: List<DnD35AttackRowInput> = emptyList(),
    val skills: List<DnD35SkillRowInput> = emptyList(),
    val feats: List<DnD35FeatRow> = emptyList(),
    val gear: List<DnD35GearRow> = emptyList(),
    val languages: List<String> = emptyList(),
    val notes: String = "",
    // spellcasting
    val spellcasting: DnD35SpellcastingInputs = DnD35SpellcastingInputs(),
) : CharacterBaseData {
    override val ruleSetId: String get() = "dnd35"
}

/** The six ability scores (base inputs). Modifiers are derived on the enriched [DnD35Abilities]. */
data class DnD35AbilityScores(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
)

/** Entered hit points — both base inputs (HP is entered in 3.5). Shared by base and enriched. */
data class DnD35HitPoints(
    val max: Int = 0,
    val current: Int = 0,
)

/** Defense base inputs; the AC totals are derived on the enriched [DnD35Defense]. */
data class DnD35DefenseInputs(
    val armorBonus: Int = 0,
    val shieldBonus: Int = 0,
    val naturalArmor: Int = 0,
    val deflection: Int = 0,
    val dodge: Int = 0,
    val sizeMod: Int = 0,
)

/** Saving-throw base inputs; the totals are derived on the enriched [DnD35Saves]. */
data class DnD35SaveInputs(
    val fortBase: Int = 0,
    val refBase: Int = 0,
    val willBase: Int = 0,
)

/** Spellcasting base inputs; the save DC and per-slot totals are derived on the enriched [DnD35Spellcasting]. */
data class DnD35SpellcastingInputs(
    val casterClass: String = "",
    val casterLevel: Int = 0,
    val spellKeyAbility: String = "",
    val spellSlots: List<DnD35SpellSlotRowInput> = emptyList(),
    val spells: List<DnD35SpellRow> = emptyList(),
)

/** A weapon/attack row's base inputs; `attackBonus` is derived on the enriched [DnD35AttackRow]. */
data class DnD35AttackRowInput(
    val weapon: String = "",
    val ability: String = "",
    val misc: Int = 0,
    val damage: String = "",
    val critical: String = "",
    val range: String = "",
    val notes: String = "",
)

/** A skill row's base inputs; `total` is derived on the enriched [DnD35SkillRow]. */
data class DnD35SkillRowInput(
    val skill: String = "",
    val keyAbility: String = "",
    val ranks: Int = 0,
    val classSkill: Boolean = false,
    val misc: Int = 0,
)

/** A per-spell-level slot row's base inputs; bonus/total are derived on the enriched [DnD35SpellSlotRow]. */
data class DnD35SpellSlotRowInput(
    val spellLevel: Int = 0,
    val slotsPerDay: Int = 0,
    val known: Int = 0,
    val prepared: Int = 0,
)

/** A feat row (T109) — no derived, so shared by base and enriched. */
data class DnD35FeatRow(
    val name: String = "",
    val type: String = "",
    val description: String = "",
)

/** A gear row (T109) — no per-row derived (`totalWeight` is a sheet-level sum); shared by base and enriched. */
data class DnD35GearRow(
    val item: String = "",
    val quantity: Int = 0,
    val weight: Int = 0,
    val notes: String = "",
)

/** A known/prepared spell row (T114) — no derived; shared by base and enriched. */
data class DnD35SpellRow(
    val spell: String = "",
    val level: Int = 0,
    val prepared: Int = 0,
    val notes: String = "",
)
