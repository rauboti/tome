import { describe, expect, test } from 'vitest'
import {
  defaultDnD35SheetInput,
  dnd35AttackBonus,
  dnd35SkillTotal,
  dnd35SpellSaveDcBase,
  dnd35SpellSlotBonus,
  dnd35SpellSlotTotal,
  enrichDnD35,
  DND35_SKILL_PRESET_COUNT,
} from './dnd35'

/**
 * T129 (client parity). The per-row derived helpers mirror the server's `enrich()` (values ported 1:1
 * from the Kotlin `DnD35CharacterDataTest`), and a fresh sheet seeds the canonical 3.5 content.
 */
describe('dnd35 typed sheet helpers', () => {
  test('a fresh sheet seeds the 31 canonical skills and spell levels 0..9', () => {
    const base = defaultDnD35SheetInput('Aria')
    expect(base.skills).toHaveLength(DND35_SKILL_PRESET_COUNT)
    expect(base.skills).toHaveLength(31)
    expect(base.skills[0]).toMatchObject({ skill: 'Appraise', keyAbility: 'intMod' })
    expect(base.spellcasting.spellSlots.map((s) => s.spellLevel)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
    expect(base.name).toBe('Aria')
  })

  test('sheet-level derived match the retired formulas (enrichDnD35)', () => {
    const base = defaultDnD35SheetInput()
    base.abilities = { ...base.abilities, strength: 14, dexterity: 16, constitution: 13, wisdom: 8 }
    base.saves = { fortBase: 2, refBase: 0, willBase: 1 }
    const s = enrichDnD35(base)
    expect(s.abilities.strMod).toBe(2)
    expect(s.abilities.dexMod).toBe(3)
    expect(s.initiative).toBe(3)
    expect(s.saves.fortitude).toBe(3) // 2 + conMod 1
    expect(s.saves.will).toBe(0) // 1 + wisMod -1
  })

  test('skill total = ranks + key-ability mod + misc', () => {
    const base = defaultDnD35SheetInput()
    base.abilities = { ...base.abilities, strength: 18 } // strMod +4
    expect(dnd35SkillTotal(base, { skill: 'Climb', keyAbility: 'strMod', ranks: 8, classSkill: true, misc: 1 })).toBe(13)
  })

  test('attack bonus = BAB + ability mod + misc', () => {
    const base = defaultDnD35SheetInput()
    base.abilities = { ...base.abilities, strength: 18 }
    base.baseAttackBonus = 6
    expect(
      dnd35AttackBonus(base, { weapon: 'Greatsword', ability: 'strMod', misc: 1, damage: '', critical: '', range: '', notes: '' }),
    ).toBe(11)
  })

  test('spell save DC base and per-level slot bonus/total (incl. level-0 zeroing)', () => {
    const base = defaultDnD35SheetInput()
    base.abilities = { ...base.abilities, intelligence: 18 } // intMod +4
    base.spellcasting = { ...base.spellcasting, spellKeyAbility: 'intMod' }
    expect(dnd35SpellSaveDcBase(base)).toBe(14)
    const slot = (spellLevel: number, slotsPerDay: number) => ({ spellLevel, slotsPerDay, known: 0, prepared: 0 })
    expect(dnd35SpellSlotBonus(base, slot(0, 4))).toBe(0) // cantrips: no bonus
    expect(dnd35SpellSlotTotal(base, slot(0, 4))).toBe(4)
    expect(dnd35SpellSlotBonus(base, slot(1, 3))).toBe(1)
    expect(dnd35SpellSlotTotal(base, slot(1, 3))).toBe(4)
    expect(dnd35SpellSlotBonus(base, slot(5, 1))).toBe(0) // mod too low for level 5
  })
})
