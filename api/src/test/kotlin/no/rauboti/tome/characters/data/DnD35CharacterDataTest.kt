package no.rauboti.tome.characters.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * T122 (TDD). Pins the **computed-derived parity** of the enriched [DnD35CharacterData] against the
 * values the retired formula engine produced — assertions ported 1:1 from `DnD35RuleSetTest`'s
 * `computeDerived` cases (T105–T111). The sheet is built by enriching a [DnD35CharacterBaseData]; the
 * derived read from their groups the way the user asked (`abilities.strMod`, `saves.fortitude`,
 * `defense.armorClass`, `spellcasting.saveDcBase`, rows with totals).
 */
class DnD35CharacterDataTest {
    private fun sheet(base: DnD35CharacterBaseData) = DnD35CharacterData(base)

    @Test
    fun `ability modifiers are floor of (score minus 10 over 2)`() {
        val a =
            sheet(
                DnD35CharacterBaseData(
                    abilities =
                        DnD35AbilityScores(
                            strength = 18,
                            dexterity = 7,
                            constitution = 10,
                            intelligence = 13,
                            wisdom = 8,
                            charisma = 20,
                        ),
                ),
            ).abilities
        assertEquals(4, a.strMod) // (18-10)/2
        assertEquals(-2, a.dexMod) // floor(-1.5) = -2
        assertEquals(0, a.conMod)
        assertEquals(1, a.intMod) // floor(3/2)
        assertEquals(-1, a.wisMod) // floor(-2/2)
        assertEquals(5, a.chaMod)
    }

    @Test
    fun `saves are base plus governing ability mod, initiative is dex mod`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(constitution = 14, dexterity = 16, wisdom = 9), // +2 / +3 / -1
                    saves = DnD35SaveInputs(fortBase = 2, refBase = 0, willBase = 6),
                ),
            )
        assertEquals(4, s.saves.fortitude) // 2 + 2
        assertEquals(3, s.saves.reflex) // 0 + 3
        assertEquals(5, s.saves.will) // 6 + (-1)
        assertEquals(3, s.initiative) // = dexMod
    }

    @Test
    fun `AC breakdown, touch and flat-footed AC, and grapple (T108)`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(strength = 14, dexterity = 16), // +2 / +3
                    baseAttackBonus = 5,
                    grappleSizeMod = 4,
                    defense =
                        DnD35DefenseInputs(
                            armorBonus = 8,
                            shieldBonus = 2,
                            naturalArmor = 1,
                            deflection = 1,
                            dodge = 1,
                            sizeMod = 0,
                        ),
                ),
            )
        assertEquals(26, s.defense.armorClass) // 10 + 8 + 2 + dex 3 + 0 + 1 + 1 + 1
        assertEquals(15, s.defense.touchAC) // 10 + dex 3 + 0 + 1 + 1
        assertEquals(22, s.defense.flatFootedAC) // 10 + 8 + 2 + 0 + 1 + 1
        assertEquals(11, s.grapple) // BAB 5 + strMod 2 + grappleSizeMod 4
    }

    @Test
    fun `a skill row total is ranks plus its key-ability mod plus misc (T106)`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(strength = 18), // strMod +4
                    skills = listOf(DnD35SkillRowInput(skill = "Climb", keyAbility = "strMod", ranks = 8, misc = 1)),
                ),
            )
        assertEquals(13, s.skills[0].total) // 8 + 4 + 1
    }

    @Test
    fun `a weapon attack bonus is BAB plus its ability mod plus misc (T107)`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(strength = 18), // +4
                    baseAttackBonus = 6,
                    attacks = listOf(DnD35AttackRowInput(weapon = "Greatsword", ability = "strMod", misc = 1)),
                ),
            )
        assertEquals(11, s.attacks[0].attackBonus) // 6 + 4 + 1
    }

    @Test
    fun `total gear weight is the sum of the gear rows' weight (T109)`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    gear =
                        listOf(
                            DnD35GearRow(item = "Greatsword", quantity = 1, weight = 8),
                            DnD35GearRow(item = "Full plate", quantity = 1, weight = 50),
                            DnD35GearRow(item = "Rations", quantity = 5, weight = 5),
                        ),
                ),
            )
        assertEquals(63, s.totalWeight) // 8 + 50 + 5
    }

    @Test
    fun `spell save DC base is 10 plus the casting ability mod (T110)`() {
        val s =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(intelligence = 18),
                    spellcasting = DnD35SpellcastingInputs(spellKeyAbility = "intMod"),
                ),
            )
        assertEquals(14, s.spellcasting.saveDcBase) // 10 + 4
    }

    @Test
    fun `per-level bonus spells (zero at level 0) and total slots (T111)`() {
        val slots =
            sheet(
                DnD35CharacterBaseData(
                    abilities = DnD35AbilityScores(intelligence = 18), // intMod +4
                    spellcasting =
                        DnD35SpellcastingInputs(
                            spellKeyAbility = "intMod",
                            spellSlots =
                                listOf(
                                    DnD35SpellSlotRowInput(spellLevel = 0, slotsPerDay = 4),
                                    DnD35SpellSlotRowInput(spellLevel = 1, slotsPerDay = 3),
                                    DnD35SpellSlotRowInput(spellLevel = 5, slotsPerDay = 1),
                                ),
                        ),
                ),
            ).spellcasting.spellSlots
        assertEquals(0, slots[0].bonusSpells) // level 0 → no bonus
        assertEquals(4, slots[0].total) // 4 + 0
        assertEquals(1, slots[1].bonusSpells) // floor((4-1)/4)+1 = 1
        assertEquals(4, slots[1].total) // 3 + 1
        assertEquals(0, slots[2].bonusSpells) // mod too low for level 5
        assertEquals(1, slots[2].total) // 1 + 0
    }

    @Test
    fun `ruleSetId is the discriminator and enrich maps each base variant`() {
        assertEquals("dnd35", DnD35CharacterData(DnD35CharacterBaseData()).ruleSetId)
        assertEquals("darksouls", DarkSoulsCharacterData(DarkSoulsCharacterBaseData()).ruleSetId)
        assertInstanceOf(DnD35CharacterData::class.java, DnD35CharacterBaseData().enrich())
        assertInstanceOf(DarkSoulsCharacterData::class.java, DarkSoulsCharacterBaseData().enrich())
    }

    @Test
    fun `derived reflect the inputs and a partial base enriches cleanly`() {
        val s = sheet(DnD35CharacterBaseData(abilities = DnD35AbilityScores(strength = 16))) // partial
        assertEquals(3, s.abilities.strMod) // floor((16-10)/2)
        assertEquals(0, s.abilities.conMod) // default constitution 10
    }
}
