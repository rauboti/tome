package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonIgnore
import kotlin.math.max
import kotlin.math.min

/**
 * D&D 3.5 **enriched** sheet (ADR-001) — the served view. Wraps a [DnD35CharacterBaseData] and exposes
 * its groups with derived values filled in, grouped the way the sheet reads: [abilities] (scores +
 * mods), [defense] (bonuses + AC), [saves] (bases + totals), [spellcasting] (inputs + save DC + slot
 * totals), and table rows carrying their per-row totals. Parity of every derived value against the
 * retired formula engine is pinned by `DnD35CharacterDataTest`.
 *
 * Never persisted (built on read by [CharacterBaseData.enrich]). The stored [base] is `@JsonIgnore`d so
 * the response is the flat enriched shape, not a `{ base: … }` wrapper; the exact response schema is
 * reconciled with openapi in T124.
 */
data class DnD35CharacterData(
    @get:JsonIgnore val base: DnD35CharacterBaseData,
) : CharacterData {
    override val ruleSetId: String get() = base.ruleSetId

    // identity (pass-through)
    val name: String get() = base.name
    val player: String get() = base.player
    val race: String get() = base.race
    val characterClass: String get() = base.characterClass
    val alignment: String get() = base.alignment
    val deity: String get() = base.deity
    val size: String get() = base.size
    val level: Int get() = base.level
    val experience: Int get() = base.experience

    // groups (enriched)
    val abilities: DnD35Abilities get() = DnD35Abilities.from(base.abilities)
    val hitPoints: DnD35HitPoints get() = base.hitPoints
    val defense: DnD35Defense get() = DnD35Defense.from(base.defense, abilities)
    val saves: DnD35Saves get() = DnD35Saves.from(base.saves, abilities)
    val spellcasting: DnD35Spellcasting get() = DnD35Spellcasting.from(base.spellcasting, abilities)

    // combat (base inputs + spanning derived)
    val baseAttackBonus: Int get() = base.baseAttackBonus
    val grappleSizeMod: Int get() = base.grappleSizeMod
    val initiative: Int get() = abilities.dexMod
    val grapple: Int get() = base.baseAttackBonus + abilities.strMod + base.grappleSizeMod

    // tables (enriched rows)
    val attacks: List<DnD35AttackRow> get() = base.attacks.map { DnD35AttackRow.from(it, base.baseAttackBonus, abilities) }
    val skills: List<DnD35SkillRow> get() = base.skills.map { DnD35SkillRow.from(it, abilities) }
    val feats: List<DnD35FeatRow> get() = base.feats
    val gear: List<DnD35GearRow> get() = base.gear
    val totalWeight: Int get() = base.gear.sumOf { it.weight }
    val languages: List<String> get() = base.languages
    val notes: String get() = base.notes
}

/** Ability scores plus their derived modifiers (`floor((score - 10) / 2)`). */
data class DnD35Abilities(
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

        fun from(s: DnD35AbilityScores): DnD35Abilities =
            DnD35Abilities(
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
            d: DnD35DefenseInputs,
            abilities: DnD35Abilities,
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

/** Saving-throw bases plus the derived totals (base + governing ability mod). */
data class DnD35Saves(
    val fortBase: Int,
    val refBase: Int,
    val willBase: Int,
    val fortitude: Int,
    val reflex: Int,
    val will: Int,
) {
    companion object {
        fun from(
            s: DnD35SaveInputs,
            abilities: DnD35Abilities,
        ): DnD35Saves =
            DnD35Saves(
                s.fortBase,
                s.refBase,
                s.willBase,
                fortitude = s.fortBase + abilities.conMod,
                reflex = s.refBase + abilities.dexMod,
                will = s.willBase + abilities.wisMod,
            )
    }
}

/** Spellcasting inputs plus the derived save-DC base and per-slot totals. */
data class DnD35Spellcasting(
    val casterClass: String,
    val casterLevel: Int,
    val spellKeyAbility: String,
    val saveDcBase: Int,
    val spellSlots: List<DnD35SpellSlotRow>,
    val spells: List<DnD35SpellRow>,
) {
    companion object {
        fun from(
            s: DnD35SpellcastingInputs,
            abilities: DnD35Abilities,
        ): DnD35Spellcasting =
            DnD35Spellcasting(
                s.casterClass,
                s.casterLevel,
                s.spellKeyAbility,
                saveDcBase = 10 + abilities.modOf(s.spellKeyAbility),
                spellSlots = s.spellSlots.map { DnD35SpellSlotRow.from(it, s.spellKeyAbility, abilities) },
                spells = s.spells,
            )
    }
}

/** A weapon/attack row with its derived attack bonus (BAB + ability mod + misc). */
data class DnD35AttackRow(
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
            r: DnD35AttackRowInput,
            baseAttackBonus: Int,
            abilities: DnD35Abilities,
        ): DnD35AttackRow =
            DnD35AttackRow(
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

/** A skill row with its derived total (ranks + key-ability mod + misc). */
data class DnD35SkillRow(
    val skill: String,
    val keyAbility: String,
    val ranks: Int,
    val classSkill: Boolean,
    val misc: Int,
    val total: Int,
) {
    companion object {
        fun from(
            r: DnD35SkillRowInput,
            abilities: DnD35Abilities,
        ): DnD35SkillRow =
            DnD35SkillRow(
                r.skill,
                r.keyAbility,
                r.ranks,
                r.classSkill,
                r.misc,
                total = r.ranks + abilities.modOf(r.keyAbility) + r.misc,
            )
    }
}

/** A per-spell-level slot row with derived bonus spells and total slots. */
data class DnD35SpellSlotRow(
    val spellLevel: Int,
    val slotsPerDay: Int,
    val known: Int,
    val prepared: Int,
    val bonusSpells: Int,
    val total: Int,
) {
    companion object {
        fun from(
            r: DnD35SpellSlotRowInput,
            spellKeyAbility: String,
            abilities: DnD35Abilities,
        ): DnD35SpellSlotRow {
            val bonus = min(r.spellLevel, 1) * max(0, Math.floorDiv(abilities.modOf(spellKeyAbility) - r.spellLevel, 4) + 1)
            return DnD35SpellSlotRow(r.spellLevel, r.slotsPerDay, r.known, r.prepared, bonusSpells = bonus, total = r.slotsPerDay + bonus)
        }
    }
}
