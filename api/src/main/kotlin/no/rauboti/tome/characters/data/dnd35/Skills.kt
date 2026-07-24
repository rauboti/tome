package no.rauboti.tome.characters.data.dnd35

/** A skill row's base inputs; `total` is derived on the enriched [DnD35Skill]. */
data class DnD35BaseSkill(
    val skill: String = "",
    val keyAbility: String = "",
    val ranks: Int = 0,
    val classSkill: Boolean = false,
    val misc: Int = 0,
)

/** A skill row with its derived total (ranks + key-ability mod + misc). */
data class DnD35Skill(
    val skill: String,
    val keyAbility: String,
    val ranks: Int,
    val classSkill: Boolean,
    val misc: Int,
    val total: Int,
) {
    companion object {
        fun from(
            r: DnD35BaseSkill,
            abilities: DnD35AbilityScores,
        ): DnD35Skill =
            DnD35Skill(
                r.skill,
                r.keyAbility,
                r.ranks,
                r.classSkill,
                r.misc,
                total = r.ranks + abilities.modOf(r.keyAbility) + r.misc,
            )
    }
}
