package no.rauboti.tome.characters.data

import com.fasterxml.jackson.annotation.JsonIgnore
import no.rauboti.tome.characters.data.dnd35.DnD35AbilityScores
import no.rauboti.tome.characters.data.dnd35.DnD35Attack
import no.rauboti.tome.characters.data.dnd35.DnD35Defense
import no.rauboti.tome.characters.data.dnd35.DnD35Feat
import no.rauboti.tome.characters.data.dnd35.DnD35Gear
import no.rauboti.tome.characters.data.dnd35.DnD35HitPoints
import no.rauboti.tome.characters.data.dnd35.DnD35Player
import no.rauboti.tome.characters.data.dnd35.DnD35Saves
import no.rauboti.tome.characters.data.dnd35.DnD35Skill
import no.rauboti.tome.characters.data.dnd35.DnD35Spellcasting

/**
 * D&D 3.5 **enriched** sheet (ADR-001) — the served view: wraps a [DnD35CharacterBaseData] and exposes
 * its groups with derived values filled in. Never persisted (built on read by [CharacterBaseData.enrich]).
 * The stored [base] is `@JsonIgnore`d so the response is the flat enriched shape, not a `{ base: … }`
 * wrapper (schema reconciled with openapi in T124).
 */
data class DnD35CharacterData(
    @get:JsonIgnore val base: DnD35CharacterBaseData,
) : CharacterData {
    override val ruleSetId: String get() = base.ruleSetId

    // identity (pass-through)
    val name: String get() = base.name
    val player: DnD35Player get() = base.player
    val race: String get() = base.race
    val characterClass: String get() = base.characterClass
    val alignment: String get() = base.alignment
    val deity: String get() = base.deity
    val size: String get() = base.size
    val level: Int get() = base.level
    val experience: Int get() = base.experience

    // groups (enriched)
    val abilities: DnD35AbilityScores get() = DnD35AbilityScores.from(base.abilities)
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
    val attacks: List<DnD35Attack> get() = base.attacks.map { DnD35Attack.from(it, base.baseAttackBonus, abilities) }
    val skills: List<DnD35Skill> get() = base.skills.map { DnD35Skill.from(it, abilities) }
    val feats: List<DnD35Feat> get() = base.feats
    val gear: List<DnD35Gear> get() = base.gear
    val totalWeight: Int get() = base.gear.sumOf { it.weight }
    val languages: List<String> get() = base.languages
    val notes: String get() = base.notes
}
