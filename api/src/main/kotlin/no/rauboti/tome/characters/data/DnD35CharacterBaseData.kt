package no.rauboti.tome.characters.data

import no.rauboti.tome.characters.data.dnd35.DnD35BaseAbilityScores
import no.rauboti.tome.characters.data.dnd35.DnD35BaseAttack
import no.rauboti.tome.characters.data.dnd35.DnD35BaseDefense
import no.rauboti.tome.characters.data.dnd35.DnD35BaseSaves
import no.rauboti.tome.characters.data.dnd35.DnD35BaseSkill
import no.rauboti.tome.characters.data.dnd35.DnD35BaseSpellcasting
import no.rauboti.tome.characters.data.dnd35.DnD35Feat
import no.rauboti.tome.characters.data.dnd35.DnD35Gear
import no.rauboti.tome.characters.data.dnd35.DnD35HitPoints
import no.rauboti.tome.characters.data.dnd35.DnD35Player
import org.springframework.data.annotation.TypeAlias

/**
 * D&D 3.5 **base inputs** (ADR-001) — the stored/parsed sheet, enriched to [DnD35CharacterData] on read
 * ([CharacterBaseData.enrich]). Identity fields stay top-level; cohesive inputs are grouped.
 * `@TypeAlias("dnd35")` pins the stored `_class` discriminator; all fields default so a partial sheet
 * (create/edit) constructs cleanly.
 */
@TypeAlias("dnd35")
data class DnD35CharacterBaseData(
    // identity
    val name: String = "",
    val player: DnD35Player = DnD35Player(),
    val race: String = "",
    val characterClass: String = "",
    val alignment: String = "",
    val deity: String = "",
    val size: String = "",
    val level: Int = 1,
    val experience: Int = 0,
    // grouped inputs
    val abilities: DnD35BaseAbilityScores = DnD35BaseAbilityScores(),
    val hitPoints: DnD35HitPoints = DnD35HitPoints(),
    val defense: DnD35BaseDefense = DnD35BaseDefense(),
    val saves: DnD35BaseSaves = DnD35BaseSaves(),
    // combat
    val baseAttackBonus: Int = 0,
    val grappleSizeMod: Int = 0,
    // tables
    val attacks: List<DnD35BaseAttack> = emptyList(),
    val skills: List<DnD35BaseSkill> = emptyList(),
    val feats: List<DnD35Feat> = emptyList(),
    val gear: List<DnD35Gear> = emptyList(),
    val languages: List<String> = emptyList(),
    val notes: String = "",
    // spellcasting
    val spellcasting: DnD35BaseSpellcasting = DnD35BaseSpellcasting(),
) : CharacterBaseData {
    override val ruleSetId: String get() = "dnd35"
}
